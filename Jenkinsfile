pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
    }

    triggers { githubPush() }

    environment {
        AWS_REGION = 'ap-south-1'
        ECR_REPOSITORY = 'online-voting'
        AWS_ACCOUNT_ID = 'CHANGE_ME'
        IMAGE_URI = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}"
        SONAR_PROJECT_KEY = 'online-voting'
        SONAR_HOST_URL = 'CHANGE_ME'
        EC2_HOST = 'CHANGE_ME'
        EC2_USER = 'ec2-user'
        APP_URL = 'CHANGE_ME'
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }
        stage('Build & Test') {
            steps { sh 'mvn -B clean verify' }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'target/*.jar,target/site/jacoco/**'
                }
            }
        }
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('sonarqube') {
                    sh 'mvn -B verify sonar:sonar -Dsonar.projectKey="$SONAR_PROJECT_KEY" -Dsonar.host.url="$SONAR_HOST_URL"'
                }
            }
        }
        stage('Quality Gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') { waitForQualityGate abortPipeline: true }
            }
        }
        stage('Docker Build') {
            steps { sh 'docker build -t "$IMAGE_URI:$BUILD_NUMBER" -t "$IMAGE_URI:latest" .' }
        }
        stage('Push to Amazon ECR') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'aws-ecr', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    sh '''
                        set +x
                        export AWS_DEFAULT_REGION="$AWS_REGION"
                        aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
                        docker push "$IMAGE_URI:$BUILD_NUMBER"
                        docker push "$IMAGE_URI:latest"
                    '''
                }
            }
        }
        stage('Deploy to EC2') {
            steps {
                sshagent(credentials: ['ec2-deploy-key']) {
                    sh '''
                        set +x
                        ssh -o StrictHostKeyChecking=no "$EC2_USER@$EC2_HOST" "set -e
                          aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
                          docker pull $IMAGE_URI:$BUILD_NUMBER
                          docker rm -f voteflow-app || true
                          docker run -d --name voteflow-app --restart unless-stopped --env-file /opt/voteflow/.env -p 8092:8092 $IMAGE_URI:$BUILD_NUMBER
                        "
                    '''
                }
            }
        }
        stage('Smoke Test') {
            steps { sh 'sleep 15 && curl --fail --silent --show-error "$APP_URL/actuator/health"' }
        }
    }

    post {
        success { echo "Deployment successful: ${env.BUILD_NUMBER}" }
        failure { echo 'Pipeline failed. Check the stage logs above.' }
        always {
            sh 'docker image prune -f || true'
            cleanWs()
        }
    }
}
