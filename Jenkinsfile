pipeline {
    agent any

    environment {
        AWS_REGION = 'eu-north-1'
        ECR_REPO_NAME = 'voting-app'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/Dm-1324/online-voting.git'
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean verify'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh '''
                        $(which sonar-scanner || echo /var/jenkins_home/tools/hudson.plugins.sonar.SonarRunnerInstallation/SonarScanner/bin/sonar-scanner) \
                        -Dsonar.projectKey=voting-app \
                        -Dsonar.projectName="Online Voting System" \
                        -Dsonar.sources=src/main \
                        -Dsonar.tests=src/test \
                        -Dsonar.java.binaries=target/classes \
                        -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                    '''
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 3, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t ${ECR_REPO_NAME}:${BUILD_NUMBER} .'
            }
        }

        stage('Push to ECR') {
            steps {
                sh '''
                    aws ecr describe-repositories --repository-names ${ECR_REPO_NAME} --region ${AWS_REGION} || \
                      aws ecr create-repository --repository-name ${ECR_REPO_NAME} --region ${AWS_REGION}

                    ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
                    aws ecr get-login-password --region ${AWS_REGION} | \
                      docker login --username AWS --password-stdin ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

                    docker tag ${ECR_REPO_NAME}:${BUILD_NUMBER} ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO_NAME}:${BUILD_NUMBER}
                    docker push ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO_NAME}:${BUILD_NUMBER}
                '''
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
                    docker rm -f voting-app-running 2>/dev/null || true
                    docker run -d --name voting-app-running -p 8092:8092 --restart unless-stopped \
                      ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO_NAME}:${BUILD_NUMBER}
                '''
            }
        }
    }

    post {
        success {
            echo "Pipeline succeeded — voting-app:${BUILD_NUMBER} is live on port 8092"
        }
        failure {
            echo "Pipeline failed — check the stage that aborted above (likely Quality Gate)"
        }
        always {
            sh 'docker system prune -f || true'
        }
    }
}
