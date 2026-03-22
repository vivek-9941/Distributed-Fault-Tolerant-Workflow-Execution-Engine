# =============================================================
# build-and-deploy.ps1
# Builds all 3 microservices and deploys everything to the
# local Kubernetes cluster running inside Docker Desktop.
# =============================================================
# Usage: Run from the PROJECT ROOT directory:
#   cd c:\Users\Lenovo\Desktop\springbt\workflow_project
#   .\k8s\build-and-deploy.ps1
# =============================================================

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Workflow Engine - Local K8s Deployment " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# -------------------------------------------
# STEP 1: Build core-models (shared library)
# -------------------------------------------
Write-Host "`n[1/7] Building core-models..." -ForegroundColor Yellow
Set-Location "$ProjectRoot\core-models"
& mvn install -DskipTests -q
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: core-models build failed" -ForegroundColor Red; exit 1 }
Write-Host "  core-models OK" -ForegroundColor Green

# -------------------------------------------
# STEP 2: Build workflow-api JAR
# -------------------------------------------
Write-Host "`n[2/7] Building workflow-api..." -ForegroundColor Yellow
Set-Location "$ProjectRoot\workflow-api"
& mvn package -DskipTests -q
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: workflow-api build failed" -ForegroundColor Red; exit 1 }
Write-Host "  workflow-api OK" -ForegroundColor Green    

# -------------------------------------------
# STEP 3: Build orchestrator-service JAR
# -------------------------------------------
Write-Host "`n[3/7] Building orchestrator-service..." -ForegroundColor Yellow
Set-Location "$ProjectRoot\orchestrator-service"
& mvn package -DskipTests -q
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: orchestrator-service build failed" -ForegroundColor Red; exit 1 }
Write-Host "  orchestrator-service OK" -ForegroundColor Green

# -------------------------------------------
# STEP 4: Build worker-service JAR
# -------------------------------------------
Write-Host "`n[4/7] Building worker-service..." -ForegroundColor Yellow
Set-Location "$ProjectRoot\worker-service"
& mvn package -DskipTests -q
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: worker-service build failed" -ForegroundColor Red; exit 1 }
Write-Host "  worker-service OK" -ForegroundColor Green

# -------------------------------------------
# STEP 5: Build Docker images
# Note: imagePullPolicy: Never in k8s means
# Kubernetes uses ONLY locally built images.
# -------------------------------------------
Write-Host "`n[5/7] Building Docker images..." -ForegroundColor Yellow
Set-Location $ProjectRoot

docker build -t workflow-api:latest       .\workflow-api
docker build -t orchestrator-service:latest .\orchestrator-service
docker build -t worker-service:latest     .\worker-service

Write-Host "  Docker images built OK" -ForegroundColor Green

# -------------------------------------------
# STEP 6: Apply k8s manifests in order
# Infrastructure first, then apps.
# -------------------------------------------
Write-Host "`n[6/7] Applying Kubernetes manifests..." -ForegroundColor Yellow
Set-Location $ProjectRoot

kubectl apply -f .\k8s\configmap.yaml
kubectl apply -f .\k8s\mysql.yaml
kubectl apply -f .\k8s\kafka.yaml

Write-Host "  Waiting 20s for MySQL and Kafka to be ready..." -ForegroundColor DarkYellow
Start-Sleep -Seconds 20

kubectl apply -f .\k8s\services.yaml
kubectl apply -f .\k8s\worker-hpa.yaml
kubectl apply -f .\k8s\prometheus.yaml
kubectl apply -f .\k8s\grafana.yaml

Write-Host "  Manifests applied OK" -ForegroundColor Green

# -------------------------------------------
# STEP 7: Show status
# -------------------------------------------
Write-Host "`n[7/7] Deployment Status:" -ForegroundColor Yellow
kubectl get pods
Write-Host ""
kubectl get services
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Done! Access points:" -ForegroundColor Cyan
Write-Host "   Workflow API : http://localhost:8081" -ForegroundColor White
Write-Host "   Prometheus   : http://localhost:9090" -ForegroundColor White
Write-Host "   Grafana      : http://localhost:3000 (admin/admin)" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
