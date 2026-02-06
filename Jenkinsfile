@Library('jenkins-shared-lib@k8s') _
def CHANGED_SERVICES = []
pipeline {
    agent {
        kubernetes {
            inheritFrom 'gradle-agent'
            defaultContainer 'gradle'
        }
    }

    environment {
        AWS_REGION        = "ap-northeast-2"
        AWS_ACCOUNT_ID    = "900808296075"
        ECR_REGISTRY      = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        GITOPS_REPO_URL   = "https://github.com/GroomCloudTeam2/courm-helm.git"
        GITOPS_BRANCH     = "agent"
        GITOPS_VALUES_BASE = "services"
        SLACK_CHANNEL     = "#jenkins-alerts"

        GRADLE_USER_HOME = "/home/jenkins/.gradle"

    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        // 동시 빌드가 꼬이지 않도록 설정
        disableConcurrentBuilds()
    }

    stages {

        stage('Prepare') {
            steps {
                script {
                    env.IMAGE_TAG = generateImageTag()
                    CHANGED_SERVICES = getChangedServices()

                    echo "IMAGE_TAG: ${env.IMAGE_TAG}"
                    echo "Changed Services: ${CHANGED_SERVICES}"
                }
            }
        }

        // 실제 작업이 있을 때만 실행되는 블록
        stage('CI/CD Process') {
            when {
                expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
            }
            stages {
                stage('Gradle Test') {
                    steps {
                        runServiceTests(
                            services: CHANGED_SERVICES,
                            excludeTags: 'Integration'
                        )
                    }
                    post {
                        always {
                            junit '**/build/test-results/test/*.xml'
                        }
                    }
                }

                stage('Build & Push') {
                    when { branch 'agent' }
                    steps {
                        jibBuildAndPush(
                            services: CHANGED_SERVICES,
                            imageTag: env.IMAGE_TAG,
                            ecrRegistry: env.ECR_REGISTRY,
                            awsRegion: env.AWS_REGION
                        )
                    }
                }

                stage('Update GitOps') {
                    when { branch 'agent' }
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
        }
    }

//     post {
//         success {
//             script {
//                 // 변경 사항이 있을 때만 슬랙 알림 전송
//                 if (CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty()) {
//                     slackNotify(status: 'SUCCESS', channel: SLACK_CHANNEL, services: CHANGED_SERVICES)
//                 }
//             }
//         }
//         failure {
//             // 실패는 변경 사항 유무와 관계없이 알림 (파이프라인 자체 오류 포함)
//             slackNotify(status: 'FAILURE', channel: SLACK_CHANNEL, services: CHANGED_SERVICES)
//         }
//         always {
//             archiveArtifacts artifacts: 'trivy-reports/*.json', allowEmptyArchive: true
//         }
//     }
}