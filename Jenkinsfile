@Library('jenkins-shared-lib@k8s') _

/**
 * 전역 변수 선언 (Prepare 스테이지에서 할당)
 */
def CHANGED_SERVICES = []

pipeline {
    agent {
        kubernetes {
            inheritFrom 'gradle-agent'
            defaultContainer 'gradle'
        }
    }

    environment {
        AWS_REGION         = "ap-northeast-2"
        AWS_ACCOUNT_ID     = "900808296075"
        ECR_REGISTRY       = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        GITOPS_REPO_URL    = "https://github.com/GroomCloudTeam2/courm-helm.git"
        GITOPS_BRANCH      = "agent"
        GITOPS_VALUES_BASE = "services"

        SLACK_CHANNEL      = "#jenkins-alerts"

        // 에이전트 Pod 내 PVC 마운트 경로와 일치해야 캐시가 작동합니다.
        GRADLE_USER_HOME   = "/home/jenkins/.gradle"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    stages {
        stage('Prepare') {
            steps {
                script {
                    // 이미지 태그 생성 및 변경된 서비스 감지
                    env.IMAGE_TAG = generateImageTag()
                    CHANGED_SERVICES = getChangedServices()

                    echo "IMAGE_TAG: ${env.IMAGE_TAG}"
                    echo "Detected Services: ${CHANGED_SERVICES}"

                    if (!CHANGED_SERVICES) {
                        currentBuild.result = 'SUCCESS'
                        error "No changed services found. Stopping pipeline gracefully."
                    }
                }
            }
        }

        stage('CI/CD Process') {
            when {
                expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
            }
            stages {
                stage('Gradle Test') {
                    steps {
                        // Shared Library를 통한 테스트 실행
                        runServiceTests(
                            services: CHANGED_SERVICES,
                            excludeTags: 'Integration'
                        )
                    }
                    post {
                        always {
                            // 파일이 없어도 빌드가 실패하지 않도록 allowEmptyResults 옵션 추가
                            junit testResults: '**/build/test-results/**/*.xml', allowEmptyResults: true
                        }
                    }
                }

                stage('Build & Push') {
                    when { branch 'agent' }
                    steps {
                        // build.gradle에 추가한 jibExtensions를 통해 IRSA 방식으로 푸시
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
                        // Helm Repo의 values.yaml 업데이트
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
//                 if (CHANGED_SERVICES) {
//                     slackNotify(status: 'SUCCESS', channel: SLACK_CHANNEL, services: CHANGED_SERVICES)
//                 }
//             }
//         }
//         failure {
//             slackNotify(status: 'FAILURE', channel: SLACK_CHANNEL, services: CHANGED_SERVICES)
//         }
//     }
}