import requests
import json
import uuid

# Configuration
API_URL = "http://localhost:8081/api/workflows/submit"
NUM_TASKS = 500
WORKFLOW_NAME = "Stress Test - Massive DAG"
USER = "load-tester"

def generate_dag(num_tasks):
    tasks = []
    
    # Tier 1: 50 Parallel generic setups
    for i in range(1, 51):
        tasks.append({
            "id": f"setup-{i}",
            "type": "BUILD",
            "command": f"echo 'Setting up environment {i}' && sleep 2",
            "timeoutSeconds": 300,
            "maxRetries": 3,
            "dependsOn": []
        })

    # Tier 2: 400 Parallel processing tasks, randomly depending on one of the setups
    import random
    for i in range(1, 401):
        parent_id = f"setup-{random.randint(1, 50)}"
        tasks.append({
            "id": f"process-{i}",
            "type": "TEST",
            "command": f"echo 'Processing task {i}' && sleep 5",
            "timeoutSeconds": 300,
            "maxRetries": 1,
            "dependsOn": [parent_id]
        })

    # Tier 3: 50 Analytics tasks, depending on chunks of processing
    for i in range(1, 51):
        parents = [f"process-{x}" for x in range((i-1)*8 + 1, (i*8) + 1)]
        tasks.append({
            "id": f"analytics-{i}",
            "type": "REPORT",
            "command": f"echo 'Running analytics block {i}' && sleep 3",
            "timeoutSeconds": 60,
            "maxRetries": 2,
            "dependsOn": parents
        })

    return {
        "workflowName": WORKFLOW_NAME,
        "triggeredBy": USER,
        "tasks": tasks
    }

def main():
    print(f"Generating workflow payload with {NUM_TASKS} tasks...")
    payload = generate_dag(NUM_TASKS)
    
    print(f"Submitting to Workflow API at {API_URL}...")
    try:
        response = requests.post(
            API_URL, 
            json=payload, 
            headers={"Content-Type": "application/json"}
        )
        response.raise_for_status()
        
        data = response.json()
        print(f"✅ Submission Successful!")
        print(f"Workflow Run ID: {data.get('id')}")
        print("You can now monitor Grafana and Kubernetes HPA to watch the cluster scale.")
        
    except requests.exceptions.RequestException as e:
        print(f"❌ Submission Failed: {e}")
        if e.response is not None:
            print(f"Server Response: {e.response.text}")

if __name__ == "__main__":
    main()
