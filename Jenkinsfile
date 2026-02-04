@Library('jenkins-shared-lib@local-demo') _

def CHANGED_SERVICES = []

pipeline {
    agent any

    environment {
        GRADLE_USER_HOME   = "/var/jenkins_home/.gradle"
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

        stage('CI') {
            when { branch 'main' }

            stages {

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
                        expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                    }
                    steps {
                        script {
                            def testTasks = CHANGED_SERVICES.collect {
                                ":service:${it}:test :service:${it}:jacocoTestReport"
                            }.join(' ')

                            sh """
                                ./gradlew ${testTasks} \
                                --no-daemon \
                                --max-workers=2 \
                                -DexcludeTags=integration
                            """
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
                        expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                    }
                    steps {
                        script {
                            def buildTasks = CHANGED_SERVICES.collect {
                                ":service:${it}:bootJar"
                            }.join(' ')

                            sh """
                                ./gradlew ${buildTasks} \
                                --no-daemon \
                                --max-workers=2 \
                                --parallel \
                                -x test
                            """
                        }
                    }
                }
            }
        }

        stage('ECR Login') {
            when {
                allOf {
                    branch 'main'
                    expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                }
            }
            steps {
                sh """
                    aws ecr get-login-password --region ${AWS_REGION} \
                    | docker login --username AWS --password-stdin ${ECR_REGISTRY}
                """
            }
        }

        stage('Docker Build & Push') {
            when {
                allOf {
                    branch 'main'
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
                    credentialsId: 'github-token',
                    usernameVariable: 'GIT_USER',
                    passwordVariable: 'GIT_TOKEN'
                )]) {
                    script {
                        def services = CHANGED_SERVICES.join(', ')

                        sh """
                            rm -rf ${GITOPS_DIR}
                            git clone https://\${GIT_USER}:\${GIT_TOKEN}@github.com/GroomCloudTeam2/courm-helm.git ${GITOPS_DIR}
                            cd ${GITOPS_DIR}
                            git checkout ${GITOPS_BRANCH}
                        """

                        CHANGED_SERVICES.each { svc ->
                            sh """
                                cd ${GITOPS_DIR}
                                sed -i 's|tag:.*|tag: "${IMAGE_TAG}"|' \
                                ${GITOPS_VALUES_BASE}/${svc}-service/values.yaml
                            """
                        }

                        sh """
                            cd ${GITOPS_DIR}
                            git config user.email "hyunho3445@gmail.com"
                            git config user.name "yyytgf123"
                            git add .
                            git commit -m "Update services [${services}] to ${IMAGE_TAG}" || echo "No changes"
                            git push origin ${GITOPS_BRANCH}
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                try {
                    slackNotify(
                        status: 'SUCCESS',
                        channel: SLACK_CHANNEL,
                        services: CHANGED_SERVICES
                    )
                } catch (Exception e) {
                    echo "Slack notification failed: ${e.message}"
                }
            }
        }
        failure {
            script {
                try {
                    slackNotify(
                        status: 'FAILURE',
                        channel: SLACK_CHANNEL,
                        services: CHANGED_SERVICES
                    )
                } catch (Exception e) {
                    echo "Slack notification failed: ${e.message}"
                }
            }
        }
        always {
            archiveArtifacts artifacts: 'trivy-reports/*.json', allowEmptyArchive: true
        }
    }
}