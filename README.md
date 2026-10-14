# CampusFix — Cloud Computing CI/CD Assignment

**Project:** CampusFix Smart Campus Issue Tracker  
**Team:** Vennela Godari (Lead), Gopi Bhukya, Saipuja Vadla *(edit if your official team details differ)*  
**Deadline:** 15 October 2026

## What it does
CampusFix lets students report campus issues (classroom, Wi-Fi, water, electrical, lab equipment, other), see the issue board, change status (Open / In Progress / Resolved), and delete reports. It has a responsive browser UI and a small JSON REST API. Three example issues are loaded at startup. **Data is stored in memory and resets when the process restarts**; persistence is intentionally out of scope for this beginner CI/CD demonstration.

## Architecture in one minute
1. **Git/GitHub** stores the source code.
2. **Maven** compiles Java and packages `target/campusfix.jar`.
3. **Jenkins** runs build and smoke tests, builds a Docker image, pushes it to Docker Hub, and applies Kubernetes manifests.
4. **Docker** packages the JAR with a Java runtime so the app runs consistently.
5. **Kubernetes** runs two Pod replicas, checks `/health`, and exposes the app using a NodePort Service.

## Prerequisites for Windows 10/11
Install:
- JDK 17 or later (set `JAVA_HOME` and add `%JAVA_HOME%\bin` to `PATH`).
- Git for Windows.
- Maven 3.9+ (extract it and add its `bin` folder to `PATH`).
- Docker Desktop, with Linux containers enabled.
- For local Kubernetes: Docker Desktop Settings → Kubernetes → Enable Kubernetes. Wait until it reports running.
- For the full pipeline: a Jenkins instance/agent with Java 17, Maven, Python 3, Docker CLI/daemon access, `kubectl`, Git, and access to your Kubernetes cluster. Jenkins running inside a container needs deliberate Docker and kubeconfig access; don't blindly mount the host socket on a shared machine.

Open a **new** Command Prompt after installation and check:
```bat
java -version
git --version
mvn -version
docker --version
kubectl version --client
```

## Run locally (without Docker first)
Open Command Prompt in this project folder:
```bat
mvn clean package
java -jar target\campusfix.jar
```
Then visit http://localhost:8080. Health check: http://localhost:8080/health. Stop with Ctrl+C.

If Maven is not installed yet, the prebuilt `target/campusfix.jar` is included in the ZIP; try `java -jar target\campusfix.jar` after installing Java 17+.

## Run with Docker
First build the JAR as above, then:
```bat
docker build -t campusfix:local .
docker run --rm -p 8080:8080 campusfix:local
```
Open http://localhost:8080. If port 8080 is occupied, stop the other app or change the host port to `-p 8081:8080` and visit http://localhost:8081.

## Run on local Kubernetes (Docker Desktop)
1. Ensure Docker Desktop Kubernetes is enabled and `kubectl config current-context` reports `docker-desktop`.
2. Build the image locally:
```bat
mvn clean package
docker build -t campusfix:local .
```
3. For a local image, create a local-only manifest by changing the deployment image to `campusfix:local` and `imagePullPolicy: IfNotPresent`. Save this change before applying; Kubernetes must be able to see the image in Docker Desktop's Kubernetes runtime.
4. Apply manifests:
```bat
kubectl apply -f k8s\deployment.yaml
kubectl apply -f k8s\service.yaml
kubectl rollout status deployment/campusfix
kubectl get pods,svc
```
5. Visit http://localhost:30080. If the local cluster cannot find `campusfix:local`, use Docker Hub steps below instead.

## Docker Hub image (for shared/demo deployment)
1. Create a Docker Hub account and create a public repository named `campusfix`.
2. In `k8s/deployment.yaml`, replace `YOUR_DOCKERHUB_USERNAME` with your Docker Hub username.
3. Build and push (replace `YOUR_USERNAME`):
```bat
docker login
docker build -t YOUR_USERNAME/campusfix:latest .
docker push YOUR_USERNAME/campusfix:latest
kubectl apply -f k8s\deployment.yaml
kubectl apply -f k8s\service.yaml
kubectl rollout status deployment/campusfix
```
4. Visit http://localhost:30080. Never put your Docker Hub password or token in source code.

## Jenkins pipeline setup
The included `Jenkinsfile` demonstrates Checkout → Build JAR → Smoke Test → Build Docker Image → Push Docker Image → Deploy to Kubernetes. This pipeline assumes a Linux Jenkins agent with `mvn`, `python3`, `docker`, `kubectl`, and cluster credentials configured.

1. Push this folder to a GitHub repository.
2. Configure a Jenkins Pipeline job from SCM, using the GitHub repository URL and script path `Jenkinsfile`.
3. Add Jenkins username/password credential ID `dockerhub-creds` (Docker Hub username + access token, not your account password).
4. Add a Jenkins **Secret file** credential ID `kubeconfig` containing a kubeconfig for the intended cluster. Ensure Jenkins has permission to deploy to that cluster.
5. In `Jenkinsfile`, replace `YOUR_DOCKERHUB_USERNAME` with your Docker Hub username. The credential ID names must match exactly.
6. Ensure the Jenkins agent's Docker daemon and Kubernetes context are configured. Run a build and inspect each stage/log. The deployment stage is intended for a properly configured Linux agent and should not be run against a production cluster without review.

> Jenkins pipeline note: the Docker Hub credential must be available during both push and deploy stages. If adapting this Jenkinsfile, bind the Docker Hub username to a pipeline-level environment variable or re-bind credentials in the deploy stage. Verify credential scope and permissions on your Jenkins installation.

## API quick reference
| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Liveness/health response |
| GET | `/api/issues` | List issues |
| POST | `/api/issues` | Create issue: `{"title":"Fan broken","category":"Electrical","description":"Room 12"}` |
| PUT | `/api/issues/{id}` | Update status: `{"status":"Resolved"}` |
| DELETE | `/api/issues/{id}` | Delete issue |

Allowed categories: `Classroom`, `Wi-Fi`, `Water`, `Electrical`, `Lab Equipment`, `Other`. Allowed statuses: `Open`, `In Progress`, `Resolved`.

## Automated smoke test
```bash
mvn clean package
python3 scripts/smoke_test.py target/campusfix.jar
```
The script starts the JAR on a test port and checks health, list, create, update, and delete. Python 3 is required for this test script.

## Suggested three-person team split
- **Member 1 — Application:** UI, API, and test the CRUD operations.
- **Member 2 — Containers:** Dockerfile, image build/push, Kubernetes Deployment and Service.
- **Member 3 — Automation & report:** Jenkins credentials/pipeline, run validation, screenshots and presentation.

## Troubleshooting
- **`mvn` not recognized:** install Maven and add its `bin` directory to PATH; reopen Command Prompt.
- **`java` not recognized:** install JDK 17+ and configure PATH/JAVA_HOME.
- **Port already in use:** stop the other process or set `PORT=8081` before running the JAR.
- **ImagePullBackOff:** confirm the image exists in Docker Hub and the manifest has the correct username/tag; private images need an image pull secret.
- **Pods not ready:** use `kubectl describe pod <pod-name>` and `kubectl logs deployment/campusfix`.
- **Jenkins cannot run Docker/kubectl:** fix the agent's Docker daemon and kubeconfig setup; installing the Jenkins UI alone does not provide cluster access.

## Submission checklist
- [ ] Source code, JAR, Dockerfile, Jenkinsfile and Kubernetes YAML included.
- [ ] Run app locally and take a genuine screenshot.
- [ ] Run smoke test and capture its output.
- [ ] Run Jenkins pipeline on a configured agent and capture successful stage view.
- [ ] Capture `kubectl get pods,svc` and the deployed browser page.
- [ ] Add genuine screenshots to slide 9 and confirm team details.
- [ ] Zip the project folder for submission.

**Scope note:** This is a teaching/demo application, not a production issue-management system. It has no login, durable database, or personal data protections because those features are not required for the simple CI/CD assignment.
