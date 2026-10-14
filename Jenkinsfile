pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }
  environment {
    APP_NAME = 'campusfix'
    DOCKERHUB_USERNAME = 'YOUR_DOCKERHUB_USERNAME'
  }
  stages {
    stage('Checkout') { steps { checkout scm } }
    stage('Build JAR') { steps { sh 'mvn -B clean package' } }
    stage('Smoke Test') { steps { sh 'python3 scripts/smoke_test.py target/campusfix.jar' } }
    stage('Build Docker Image') {
      steps { sh 'docker build -t "$DOCKERHUB_USERNAME/campusfix:${BUILD_NUMBER}" -t "$DOCKERHUB_USERNAME/campusfix:latest" .' }
    }
    stage('Push Docker Image') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DH_USER', passwordVariable: 'DH_TOKEN')]) {
          sh 'echo "$DH_TOKEN" | docker login -u "$DH_USER" --password-stdin'
          sh 'docker push "$DH_USER/campusfix:${BUILD_NUMBER}"'
          sh 'docker push "$DH_USER/campusfix:latest"'
          sh 'docker logout'
        }
      }
    }
    stage('Deploy to Kubernetes') {
      steps {
        withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG'), usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DH_USER', passwordVariable: 'DH_TOKEN')]) {
          sh 'sed "s|YOUR_DOCKERHUB_USERNAME|$DH_USER|g" k8s/deployment.yaml | kubectl apply -f -'
          sh 'kubectl set image deployment/campusfix campusfix="$DH_USER/campusfix:${BUILD_NUMBER}"'
          sh 'kubectl rollout status deployment/campusfix --timeout=120s'
          sh 'kubectl get pods,svc -l app=campusfix'
        }
      }
    }
  }
  post { always { archiveArtifacts artifacts: 'target/campusfix.jar', fingerprint: true, allowEmptyArchive: true } }
}
