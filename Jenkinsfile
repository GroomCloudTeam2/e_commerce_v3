@Library('jenkins-shared-lib@local-demo') _

def CHANGED_SERVICES = []

pipeline {
    agent any

    environment {
        GRADLE_USER_HOME = "/var/jenkins_home/.gradle"

        AWS_REGION     = "ap-northeast-2"
        AWS_ACCOUNT_ID = "900808296075"
        ECR_REGISTRY   = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        IMAGE_TAG = "${BUILD_NUMBER}-${GIT_COMMIT[0..7]}"

        GITOPS_REPO_URL    = "https://github.com/GroomCloudTeam2/courm-helm.git"
        GITOPS_DIR         = "courm-helm"
        GITOPS_BRANCH      = "local_test"
        GITOPS_VALUES_BASE = "services"

        SLACK_CHANNEL = "#jenkins-alerts"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {

        stage('CI') {
            when { branch 'BlueGreen' }
            stages {

                stage('Detect Changes') {
                    steps {
                        script {
                            CHANGED_SERVICES = getChangedServices()
                            echo "Changed services: ${CHANGED_SERVICES}"
                        }
                    }
                }

                stage('Gradle Build') {
                    when {
                        expression {
                            CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty()
                        }
                    }
                    steps {
                        sh """
                          ./gradlew \
                          ${CHANGED_SERVICES.collect { ":service:${it}:bootJar" }.join(' ')} \
                          --no-daemon --parallel
                        """
                    }
                }
            }
        }

        stage('ECR Login') {
            when {
                allOf {
                    branch 'BlueGreen'
                    expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                }
            }
            steps {
                sh '''
                  aws ecr get-login-password --region ${AWS_REGION} \
                  | docker login --username AWS --password-stdin ${ECR_REGISTRY}
                '''
            }
        }

        stage('Docker Build & Push') {
            when {
                allOf {
                    branch 'BlueGreen'
                    expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                }
            }
            steps {
                script {
                    parallel CHANGED_SERVICES.collectEntries { svc ->
                        [(svc): {
                            buildDockerImage(svc, IMAGE_TAG)
                        }]
                    }
                }
            }
        }

        stage('Update GitOps Repo') {
            when {
                expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'github-credentials',
                    usernameVariable: 'GIT_USER',
                    passwordVariable: 'GIT_TOKEN'
                )]) {
                    sh """
                        rm -rf ${GITOPS_DIR}
                        git clone https://${GIT_USER}:${GIT_TOKEN}@github.com/GroomCloudTeam2/courm-helm.git ${GITOPS_DIR}
                        cd ${GITOPS_DIR}
                        git checkout ${GITOPS_BRANCH}
                    """

                    script {
                        CHANGED_SERVICES.each { svc ->
                            sh """
                                cd ${GITOPS_DIR}
                                sed -i 's|tag:.*|tag: "${IMAGE_TAG}"|' ${GITOPS_VALUES_BASE}/${svc}/values.yaml
                            """
                        }
                    }

                    sh """
                        cd ${GITOPS_DIR}
                        git config user.email "jenkins@example.com"
                        git config user.name "Jenkins"
                        git add .
                        git commit -m "Update services [${CHANGED_SERVICES.join(', ')}] to ${IMAGE_TAG}" || echo "No changes"
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
