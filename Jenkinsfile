@Library('jenkins-shared-lib@k8s') _

def CHANGED_SERVICES = []

/* =========================
 * Helper
 * ========================= */
def chunk(List list, int size) {
    return list.collate(size)
}

pipeline {
    agent none

    environment {
        AWS_REGION         = "ap-northeast-2"
        AWS_ACCOUNT_ID     = "900808296075"
        ECR_REGISTRY       = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        GITOPS_REPO_URL    = "https://github.com/GroomCloudTeam2/courm-service.git"
        GITOPS_BRANCH      = "main"
        GITOPS_VALUES_BASE = "services"

        SLACK_CHANNEL      = "#jenkins-alerts"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    stages {

        /* =========================
         * Prepare
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
                        error "No changed services found. Stop pipeline."
                    }
                }
            }
        }

        /* =========================
         * CI / CD
         * ========================= */
        stage('CI/CD Process') {
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
                script {

                    /* ---------------------
                     * Checkout (1회)
                     * --------------------- */
                    checkout scm

                    /* =====================
                     * 1. TEST (max 2 parallel)
                     * ===================== */
                    echo "🧪 Test stage"

                    chunk(CHANGED_SERVICES, 2).each { svcGroup ->

                        def testStages = [:]

                        svcGroup.each { svc ->
                            testStages["Test :: ${svc}"] = {
                                stage("Test :: ${svc}") {
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
                        }

                        parallel testStages
                    }

                    /* =====================
                     * 2. BUILD & PUSH (serial)
                     * ===================== */
                    if (env.BRANCH_NAME == 'main') {
                        echo "🐳 Build & Push (serial)"

                        CHANGED_SERVICES.each { svc ->
                            stage("Build & Push :: ${svc}") {
                                jibBuildAndPush(
                                    services: [svc],
                                    imageTag: env.IMAGE_TAG,
                                    ecrRegistry: env.ECR_REGISTRY,
                                    awsRegion: env.AWS_REGION
                                )
                            }
                        }
                    } else {
                        echo "Skipping Build & Push (branch: ${env.BRANCH_NAME})"
                    }

                    /* =====================
                     * 3. GITOPS (serial + lock)
                     * ===================== */
                    if (env.BRANCH_NAME == 'main') {
                        lock(resource: 'gitops-main-branch') {
                            CHANGED_SERVICES.each { svc ->
                                stage("GitOps :: ${svc}") {
                                    updateGitOpsImageTag(
                                        repoUrl: GITOPS_REPO_URL,
                                        branch: GITOPS_BRANCH,
                                        services: [svc],
                                        imageTag: env.IMAGE_TAG,
                                        valuesBaseDir: GITOPS_VALUES_BASE
                                    )
                                }
                            }
                        }
                    } else {
                        echo "Skipping GitOps update (branch: ${env.BRANCH_NAME})"
                    }
                }
            }
        }
    }
}
