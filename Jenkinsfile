@Library('jenkins-shared-lib@local-demo') _

def CHANGED_SERVICES = []

pipeline {
    agent none

    environment {
        GRADLE_USER_HOME   = "/home/jenkins/.gradle"
        AWS_REGION         = "ap-northeast-2"
        AWS_ACCOUNT_ID     = "900808296075"
        ECR_REGISTRY       = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        IMAGE_TAG          = "${BUILD_NUMBER}-${GIT_COMMIT[0..7]}"
        GITOPS_REPO_URL    = "https://github.com/GroomCloudTeam2/courm-helm.git"
        GITOPS_DIR         = "courm-helm"
        GITOPS_BRANCH      = "main"
        GITOPS_VALUES_BASE = "services"
        SLACK_CHANNEL      = "#jenkins-alerts"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {

        /* ================= CI ================= */
        stage('CI') {
            when { branch 'agent' }

            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    defaultContainer 'gradle'
                }
            }

            stages {

                stage('Checkout') {
                    steps {
                        checkout scm
                    }
                }

                stage('Detect Changes') {
                    steps {
                        script {
                            CHANGED_SERVICES = getChangedServices()
                            echo "Changed services: ${CHANGED_SERVICES}"
                        }
                    }
                }

                stage('Gradle Test') {
                    when {
                        expression { CHANGED_SERVICES }
                    }
                    steps {
                        script {
                            def tasks = CHANGED_SERVICES
                                .collect { ":service:${it}:test :service:${it}:jacocoTestReport" }
                                .join(' ')
                            sh "./gradlew ${tasks} -DexcludeTags=Integration --no-daemon --parallel"
                        }
                    }
                    post {
                        always {
                            junit '**/build/test-results/test/*.xml'
                        }
                    }
                }

                stage('Gradle Build') {
                    when {
                        expression { CHANGED_SERVICES }
                    }
                    steps {
                        script {
                            def tasks = CHANGED_SERVICES
                                .collect { ":service:${it}:bootJar" }
                                .join(' ')
                            sh "./gradlew ${tasks} --no-daemon --parallel -x test"
                        }
                    }
                }
            }
        }

        /* ========== Docker Build & Push (Kaniko) ========== */
        stage('Docker Build & Push') {
            when {
                allOf {
                    branch 'agent'
                    expression { CHANGED_SERVICES }
                }
            }

            agent {
                kubernetes {
                    inheritFrom 'kaniko-agent'
                    defaultContainer 'kaniko'
                }
            }

            steps {
                script {
                    parallel CHANGED_SERVICES.collectEntries { svc ->
                        [(svc): {
                            sh """
                              /kaniko/executor \
                                --context=\$(pwd) \
                                --dockerfile=service/${svc}/Dockerfile \
                                --destination=${ECR_REGISTRY}/${svc}:${IMAGE_TAG} \
                                --destination=${ECR_REGISTRY}/${svc}:latest \
                                --cache=true
                            """
                        }]
                    }
                }
            }
        }

        /* ================= GitOps ================= */
        stage('Update GitOps Repo') {
            when {
                expression { CHANGED_SERVICES }
            }
            agent any

            steps {
                withCredentials([
                    string(credentialsId: 'github-token', variable: 'GIT_TOKEN')
                ]) {
                    sh """
                        rm -rf ${GITOPS_DIR}
                        git clone https://x-access-token:${GIT_TOKEN}@github.com/GroomCloudTeam2/courm-helm.git ${GITOPS_DIR}
                        cd ${GITOPS_DIR}
                        git checkout ${GITOPS_BRANCH}
                    """

                    script {
                        CHANGED_SERVICES.each { svc ->
                            sh """
                              sed -i 's|tag:.*|tag: "${IMAGE_TAG}"|' \
                              ${GITOPS_DIR}/${GITOPS_VALUES_BASE}/${svc}-service/values.yaml
                            """
                        }
                    }

                    sh """
                        cd ${GITOPS_DIR}
                        git config user.email "hyunho3445@gmail.com"
                        git config user.name "yyytgf123"
                        git add .
                        git commit -m "Update services [${CHANGED_SERVICES.join(', ')}] to ${IMAGE_TAG}" || true
                        git push origin ${GITOPS_BRANCH}
                    """
                }
            }
        }
    }

    post {
        success {
            slackNotify(
                status: 'SUCCESS',
                channel: SLACK_CHANNEL,
                services: CHANGED_SERVICES
            )
        }
        failure {
            slackNotify(
                status: 'FAILURE',
                channel: SLACK_CHANNEL,
                services: CHANGED_SERVICES
            )
        }
        always {
            archiveArtifacts artifacts: 'trivy-reports/*.json', allowEmptyArchive: true
        }
    }
}
