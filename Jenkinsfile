@Library('jenkins-shared-lib@k8s') _

def CHANGED_SERVICES = []

pipeline {
    agent none

    environment {
        AWS_REGION         = "ap-northeast-2"
        AWS_ACCOUNT_ID     = "900808296075"
        ECR_REGISTRY       = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        GITOPS_REPO_URL    = "https://github.com/GroomCloudTeam2/courm-services.git"
        GITOPS_BRANCH      = "agent"
        GITOPS_VALUES_BASE = "services"

        SLACK_CHANNEL      = "#jenkins-alerts"
        GRADLE_USER_HOME   = "/home/jenkins/.gradle"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    stages {

        /* =========================
         * Prepare Stage
         * ========================= */
        stage('Prepare') {
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    defaultContainer 'gradle'
                }
            }
            steps {
                script {
                    env.IMAGE_TAG = generateImageTag()
                    CHANGED_SERVICES = getChangedServices()

                    echo "IMAGE_TAG: ${env.IMAGE_TAG}"
                    echo "Detected Services: ${CHANGED_SERVICES}"

                    if (!CHANGED_SERVICES || CHANGED_SERVICES.isEmpty()) {
                        currentBuild.result = 'SUCCESS'
                        error "No changed services found. Stopping pipeline gracefully."
                    }
                }
            }
        }

        /* =========================
         * CI/CD Process (Parallel)
         * ========================= */
        stage('CI/CD Process') {
            when {
                expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
            }
            steps {
                script {
                    def parallelStages = [:]

                    CHANGED_SERVICES.each { svc ->
                        parallelStages["Service :: ${svc}"] = {

                            podTemplate(
                                inheritFrom: 'gradle-agent',
                                label: "gradle-agent-${svc}-${env.BUILD_NUMBER}"
                            ) {
                                node("gradle-agent-${svc}-${env.BUILD_NUMBER}") {

                                    // Checkout code
                                    checkout scm

                                    /* ---------- Test ---------- */
                                    container('gradle') {
                                        stage("${svc} :: Test") {
                                            runServiceTests(
                                                services: [svc],
                                                excludeTags: 'Integration'
                                            )

                                            junit(
                                                testResults: "**/${svc}/build/test-results/**/*.xml",
                                                allowEmptyResults: true
                                            )
                                        }
                                    }

                                    /* ---------- Build & Push ---------- */
                                    stage("${svc} :: Build & Push") {
                                        if (env.BRANCH_NAME == 'agent') {
                                            jibBuildAndPush(
                                                services: [svc],
                                                imageTag: env.IMAGE_TAG,
                                                ecrRegistry: env.ECR_REGISTRY,
                                                awsRegion: env.AWS_REGION
                                            )
                                        } else {
                                            echo "Skipping Build & Push (branch: ${env.BRANCH_NAME})"
                                        }
                                    }

                                    /* ---------- Update GitOps ---------- */
                                    container('gradle') {
                                        stage("${svc} :: Update GitOps") {
                                            if (env.BRANCH_NAME == 'agent') {
                                                updateGitOpsImageTag(
                                                    repoUrl: GITOPS_REPO_URL,
                                                    branch: GITOPS_BRANCH,
                                                    services: [svc],
                                                    imageTag: env.IMAGE_TAG,
                                                    valuesBaseDir: GITOPS_VALUES_BASE
                                                )
                                            } else {
                                                echo "Skipping GitOps update (branch: ${env.BRANCH_NAME})"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    parallel parallelStages
                }
            }
        }
    }

    /* =========================
     * Post Actions (Optional)
     * ========================= */
//    post {
//        success {
//            script {
//                if (CHANGED_SERVICES) {
//                    slackNotify(
//                        status: 'SUCCESS',
//                        channel: SLACK_CHANNEL,
//                        services: CHANGED_SERVICES
//                    )
//                }
//            }
//        }
//        failure {
//            slackNotify(
//                status: 'FAILURE',
//                channel: SLACK_CHANNEL,
//                services: CHANGED_SERVICES
//            )
//        }
//    }
}