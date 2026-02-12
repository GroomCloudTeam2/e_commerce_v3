@Library('jenkins-shared-lib@k8s') _

def CHANGED_SERVICES = []
def MAX_PARALLEL = 2

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
         * 1. Prepare (1 Pod)
         * ========================= */
        stage('Prepare') {
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    namespace 'jenkins-agent'
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
                        return
                    }
                }
            }
        }

        /* =========================
         * 2. TEST (Pod-level parallel, max 2)
         * ========================= */
        stage('Test') {
            when {
                expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
            }
            steps {
                script {
                    CHANGED_SERVICES.collate(MAX_PARALLEL).each { batch ->
                        def testStages = [:]

                        batch.each { svc ->
                            def serviceName = svc
                            testStages["Test :: ${svc}"] = {
                                def label = "gradle-test-${UUID.randomUUID().toString()}"
                                podTemplate(
                                    label: label,
                                    inheritFrom: 'gradle-agent',
                                    namespace: 'jenkins-agent',
                                    serviceAccount: 'jenkins-agent-sa'
                                ) {
                                    node(label) {
                                        container('gradle') {
                                            stage("Test :: ${serviceName}") {
                                                checkout scm

                                                runServiceTests(
                                                    services: [serviceName],
                                                    excludeTags: 'Integration'
                                                )

                                                junit(
                                                    testResults: "**/${serviceName}/build/test-results/**/*.xml",
                                                    allowEmptyResults: true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        parallel testStages
                    }
                }
            }
        }

        /* =========================
         * 3. BUILD & PUSH (Pod-level parallel, max 2)
         * ========================= */
        stage('Build & Push') {
            when {
                allOf {
                    expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                    branch 'main'
                }
            }
            steps {
                script {
                    CHANGED_SERVICES.collate(MAX_PARALLEL).each { batch ->
                        def buildStages = [:]

                        batch.each { svc ->
                            def serviceName = svc
                            buildStages["Build & Push :: ${svc}"] = {
                                def label = "gradle-build-${UUID.randomUUID().toString()}"
                                podTemplate(
                                    label: label,
                                    inheritFrom: 'gradle-agent',
                                    namespace: 'jenkins-agent',
                                    serviceAccount: 'jenkins-agent-sa'
                                ) {
                                    node(label) {
                                        container('gradle') {
                                            stage("Build & Push :: ${serviceName}") {
                                                checkout scm

                                                jibBuildAndPush(
                                                    services: [serviceName],
                                                    imageTag: env.IMAGE_TAG,
                                                    ecrRegistry: env.ECR_REGISTRY,
                                                    awsRegion: env.AWS_REGION
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        parallel buildStages
                    }
                }
            }
        }

        /* =========================
         * 4. GITOPS (serial + lock)
         * ========================= */
        stage('GitOps') {
            when {
                branch 'main'
            }
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    namespace 'jenkins-agent'
                    defaultContainer 'gradle'
                }
            }
            steps {
                lock(resource: 'gitops-main-branch') {
                    script {
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
                }
            }
        }
    }
}