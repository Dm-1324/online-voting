pipeline {
    agent any

    environment {
        AWS_REGION = 'eu-north-1'
        ECR_REPO_NAME = 'voting-app'
    }

    stages {
        stage('Build') {
            steps {
                sh 'mvn clean verify'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh '''
                        export MAVEN_OPTS="-Xmx1024m"
                        mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.10.0.2594:sonar \
                          -Dsonar.projectKey=voting-app \
                          -Dsonar.projectName="Online Voting System" \
                          -Dsonar.sources=src/main/java \
                          -Dsonar.tests=src/test \
                          -Dsonar.exclusions=src/main/resources/static/** \
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
                withCredentials([
                    string(credentialsId: 'neon-db-password', variable: 'NEON_DB_PASSWORD'),
                    usernamePassword(credentialsId: 'admin-credentials',
                                     usernameVariable: 'ADMIN_USERNAME',
                                     passwordVariable: 'ADMIN_PASSWORD')
                ]) {
                    sh '''
                        ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
                        docker rm -f voting-app-running 2>/dev/null || true
                        docker run -d --name voting-app-running -p 8092:8092 --restart unless-stopped \
                          -e DATABASE_URL="jdbc:postgresql://ep-sweet-term-zalx3lt8-pooler.c-2.eu-west-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require" \
                          -e DATABASE_USERNAME="neondb_owner" \
                          -e DATABASE_PASSWORD="${NEON_DB_PASSWORD}" \
                          -e ADMIN_USERNAME="${ADMIN_USERNAME}" \
                          -e ADMIN_PASSWORD="${ADMIN_PASSWORD}" \
                          -e DDL_AUTO="update" \
                          ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO_NAME}:${BUILD_NUMBER}
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline succeeded — voting-app:${BUILD_NUMBER} is live on port 8092, using Neon PostgreSQL"
        }
        failure {
            echo "Pipeline failed — check the stage that aborted above"
        }
        always {
            sh 'docker system prune -f || true'
        }
    }
}
