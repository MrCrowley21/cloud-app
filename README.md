# Getting Started

### Running the Application

```bash
./gradlew bootRun
```

Open [http://localhost:8080](http://localhost:8080) in your browser.

### Building the Application

```bash
./gradlew bootJar
```

### Building the JAR file to run the application locally and in Docker

```bash
java -jar ./build/libs/cloud-app-0.0.1-SNAPSHOT.jar
```

### Running the Application as a Docker Container

Not to keep track of the image version and to ensure it has a proper name, rather 
than one randomly assigned by the system, all the images are built with the tag 
`latest`. At the same time, having the same name, prunes the system to use already 
existing data, stored in the cache. To avoid this, the `--no-cache` flag is added, 
ensuring every time the image is built from scratch.

```bash
...
docker build --no-cache -t mrcroeley21/cloud-app:latest .
docker run -p 8080:8080 mrcroeley21/cloud-app:latest
```

### Pushing the image to the DockerHub

```bash
docker push mrcrowley21/cloud_app:latest
```

### Running the app in Kubernetes

```bash
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

Instead of manually running the deployment every time, the process is automated by 
creating a `deployment.yaml` file, where the deployment config is listed. Due to 
the config file, it is possible to specify things like:
* type of action: `kind: Deployment` - indicates that this manifest describes a 
Deployment, which manages pod replicas.
* matadata about the deployment: `metadata.name: cloud-app` - defines the name of 
the Deployment resource as cloud-app.
* name binding: `spec.selector.matchLabels.app: cloud-app` - matches pods with the 
app=cloud-app label; required to bind the Deployment to its pods.
* readiness and live check - ensures that no application downtime in case any pod 
is not ready to start or failed.
* resources - indicates the normal and maximum resource allocation for a pod.

Generally, this approach was preferred because:
* everything is version-controlled;
* it is easier to update or rollback changes by editing the YAML
* it integrates better with CI/CD pipelines.

Also, to expose the right port even if there are a set of pods for the same 
application, the `service.yaml` was created. It provides a consistent way to 
communicate with them, even if they are killed, restarted or scheduled. It is 
important for internal communication between components or for exposing an app 
externally.

### Manually scale the application

```bash
kubectl scale deployments/cloud-app --replicas=3
```

### Update without downtime

```yaml
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
```

To update the application without downtime, it was just added an updating strategy 
in the deployment strategy. It ensures that there are no unavailable pods during 
the update, and it also allows one additional replica, above the permitted number 
of replicas, during the updates.

### Rollback to a previous version

```bash
kubectl rollout undo deployments/cloud-app
```

### Autoscaling the application

```bash
kubectl apply -f hpa.yaml
```

For the autoscaling, it was decided to use HorizontalPodAutoscaler. It is possible 
to use the same technique from the CLI, however a .yaml version offer a series of 
benefits including:
* versioning - with .yaml, changes to autoscaling behaviour are tracked in Git, 
making it easier to review or rollback;
* reusability - can be reused across environments (dev, staging, prod) and reapplied 
without manual CLI interaction;
* idempotency - applying it repeatedly doesn’t create duplicates or inconsistent 
states - it ensures the system matches the defined desired state.
* automation friendly - easier into CI/CD pipelines and GitHub Actions workflows, 
enabling autoscaling configurations to be automatically applied upon deployment.


### Logging and monitoring

The monitoring is done via Promtail + Loki + Grafana.First, several helping files 
were created, including:
* `promtail.yaml` - it makes Promtail capable of discovering all pods automatically 
and forwarding their logs to Loki with the right labels for querying in Grafana;
* `promtain-daemonset` - ensures that logs from all pods on all nodes are collected 
and forwarded to Loki; without a DaemonSet, logs from other nodes might be missed;
* `promtai-serviceacount.yaml` - ensures clean role separation, and can be extended 
with RBAC if needed in the future.

Loki is a log aggregation system developed by Grafana Labs, designed to work 
similarly to Prometheus, but for logs. It is specifically designed to integrate 
easily with Kubernetes metadata (pod name, container name, namespace).

To set up the Promtail with Loki and Grafana, it is necessary to install them via 
helm, following the instructions below.

```bash
# Add and update helm repo
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

#Install Loki stack
helm install loki-stack grafana/loki-stack --namespace monitoring --create-namespace

# Get Grafana Admin password
kubectl get secret --namespace monitoring loki-stack-grafana   -o 
jsonpath="{.data.admin-password}" | base64 --decode ; echo

# Check the secrets in the namespace
kubectl get secrets -n monitoring

# Applying Promtail Configs
kubectl apply -f promtail.yaml
kubectl apply -f promtail-daemonset.yaml
kubectl apply -f promtail-serviceaccount.yaml
```

To avoid every time port-forwarding for Grafana, it was created a` values.yaml` file that does it automatically. To apply the changes, it is sufficient to run the command specified below.

```bash
helm upgrade --install loki-stack grafana/loki-stack -n monitoring -f values.yaml
```

### Git Actions

There were created two Git Actions files.

The first one is responsible for creating a new version of the image and deploying 
it once a new app version is pushed to the remote repository. To be able to deploy 
the application locally, even if the GitHub repo is public, it was created a local 
runner, was given access to the repo. 

The created `runner.yaml` creates a custom Kubernetes resource of kind `Runner`, 
managed by the Actions Runner Controller (ARC).
Once applied, the following happens:
1. The ARC watches GitHub for workflows that request a self-hosted runner.
2. When a workflow is triggered, ARC automatically spins up a pod that runs the 
job using the official GitHub Actions runner Docker image.
3. After job completion, the pod is cleaned up.

```bash
# Add and update the Jetstack Helm repo
helm repo add jetstack https://charts.jetstack.io
helm repo update

# Install cert-manager
helm install cert-manager jetstack/cert-manager   --namespace cert-manager   --create-namespace   --set installCRDs=true

# Add the ARC Helm chart repo
helm repo add actions-runner-controller https://actions-runner-controller.github.io/actions-runner-controller
helm repo update

# Install the Actions Runner Controller
helm install actions-runner-controller actions-runner-controller/actions-runner-controller \
  --namespace actions-runner-system \
  --create-namespace

# Create the GitHub secret for ARC
kubectl create secret generic controller-manager -n actions-runner-system --from-literal=github_token=<your_token>

# Deploy the self-hosted runner
kubectl apply -f runner.yaml
```

Also, because the GitHub Actions workflow uses `kubectl` commands, these require 
permission to update and watch deployments. Without this Role, those commands fail 
with forbidden errors. In that scope, a `role.yaml` file was added, to clearly 
specify the permissions of the GitHub Actions.

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: deployment-manager
  namespace: default
rules:
  - apiGroups: ["apps"]
    resources: ["deployments"]
    verbs: ["get", "list", "watch", "patch", "update"]
  - apiGroups: ["apps"]
    resources: ["replicasets"]
    verbs: ["get", "list", "watch"]
```

Kubernetes RBAC is namespace-scoped. Even if the pod is running with a service 
account, it cannot interact with another namespace (like `default`) unless explicitly 
granted. To handle this, a different file `role_binding`  was added, as the 
self-hosted runner pod to update and monitor the deployments it's supposed to manage.

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: runner-updater-binding
  namespace: default
subjects:
  - kind: ServiceAccount
    name: default
    namespace: actions-runner-system
roleRef:
  kind: Role
  name: deployment-manager
  apiGroup: rbac.authorization.k8s.io
```

The second GitHub Action has the goal to rollback the application. To make it more 
intuitive and easier, the rollbacks were set to be managed from the GitHub dashboard 
and react only when they are specifically requested. Each request rolls back the 
application by one deployment.

```yml
name: Rollback Deployment

on:
  workflow_dispatch:

jobs:
  rollback:
    runs-on: self-hosted
    steps:
      - name: Checkout Repo
        uses: actions/checkout@v4

      - name: Setup kubectl
        uses: azure/setup-kubectl@v3
        with:
          version: 'v1.29.2'

      - name: Rollback deployment
        run: |
          echo "Rolling back to the previous deployment..."
          kubectl rollout undo deployment/cloud-app -n default

```

### Requirements

1. This project should be made to run as a Docker image.
2. Docker image should be published to a Docker registry.
3. Docker image should be deployed to a Kubernetes cluster.
4. Kubernetes cluster should be running on a cloud provider.
5. Kubernetes cluster should be accessible from the internet.
6. Kubernetes cluster should be able to scale the application.
7. Kubernetes cluster should be able to update the application without downtime.
8. Kubernetes cluster should be able to rollback the application to a previous version.
9. Kubernetes cluster should be able to monitor the application.
10. Kubernetes cluster should be able to autoscale the application based on the load.

### Additional
1. Application logs should be stored in a centralised logging system (Loki, Kibana, etc.)
2. Application should be able to send metrics to a monitoring system.
3. Database should be running on a separate container.
4. Storage should be mounted to the database container.
