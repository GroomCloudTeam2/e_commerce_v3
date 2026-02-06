@Library('jenkins-shared-lib@k8s') _

def CHANGED_SERVICES = []

pipeline {
    agent none

    environment {
        AWS_REGION     = "ap-northeast-2"
        AWS_ACCOUNT_ID = "900808296075"
        ECR_REGISTRY   = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        GITOPS_REPO_URL    = "https://github.com/GroomCloudTeam2/courm-helm.git"
        GITOPS_BRANCH      = "agent"
        GITOPS_VALUES_BASE = "services"

        SLACK_CHANNEL  = "#jenkins-alerts"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {

        /* ===============================
         * Init
         * =============================== */
        stage('Init') {
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    defaultContainer 'gradle'
                }
            }
            steps {
                script {
                    // pod 기반 agent → workspace 캐시 안전
                    env.GRADLE_USER_HOME = "${env.WORKSPACE}/.gradle"

                    env.IMAGE_TAG = generateImageTag()

                    echo "GRADLE_USER_HOME=${env.GRADLE_USER_HOME}"
                    echo "IMAGE_TAG=${env.IMAGE_TAG}"
                }
            }
        }

        /* ===============================
         * Detect Changes
         * =============================== */
        stage('Detect Changes') {
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    defaultContainer 'gradle'
                }
            }
            steps {
                script {
                    CHANGED_SERVICES = getChangedServices()
                    echo "Changed services: ${CHANGED_SERVICES}"

                    if (!CHANGED_SERVICES || CHANGED_SERVICES.isEmpty()) {
                        echo "No changed services. Pipeline will skip build & deploy."
                    }
                }
            }
        }

        /* ===============================
         * Gradle Test
         * =============================== */
        stage('Gradle Test') {
            when {
                expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
            }
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    defaultContainer 'gradle'
                }
            }
            steps {
                runServiceTests(
                    services: CHANGED_SERVICES,
                    excludeTags: 'Integration,container'
                )
            }
            post {
                always {
                    junit '**/build/test-results/test/*.xml'
                }
            }
        }

        /* ===============================
         * Jib Build & Push
         * =============================== */
        stage('Jib Build & Push') {
            when {
                allOf {
                    branch 'agent'
                    expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                }
            }
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    defaultContainer 'gradle'
                }
            }
            steps {
                jibBuildAndPush(
                    services: CHANGED_SERVICES,
                    imageTag: env.IMAGE_TAG,
                    ecrRegistry: env.ECR_REGISTRY,
                    awsRegion: env.AWS_REGION
                )
            }
        }

        /* ===============================
         * Update GitOps Repo (Argo CD Trigger)
         * =============================== */
        stage('Update GitOps Repo') {
            when {
                allOf {
                    branch 'agent'
                    expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                }
            }
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    defaultContainer 'gradle'
                }
            }
            steps {
                updateGitOpsImageTag(
                    repoUrl: GITOPS_REPO_URL,
                    branch: GITOPS_BRANCH,
                    services: CHANGED_SERVICES,
                    imageTag: env.IMAGE_TAG,
                    valuesBaseDir: GITOPS_VALUES_BASE
                )
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
            node {
                archiveArtifacts artifacts: 'trivy-reports/*.json',
                                 allowEmptyArchive: true
            }
        }
    }
}
