pipeline {
    agent any

    environment {
        GRADLE_USER_HOME = "${WORKSPACE}/.gradle"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Clean All') {
            steps {
                sh './gradlew clean'
            }
        }

        stage('Build All Modules') {
            steps {
                sh """
                    ./gradlew \
                      :service:common:build \
                      :service:user:build \
                      :service:cart:build \
                      :service:order:build \
                      :service:payment:build \
                      :service:product:build
                """
            }
        }

        stage('JaCoCo Report') {
            steps {
                // 루트 프로젝트에 jacocoTestReport 태스크가 있다면 이 한 줄로 끝
                sh './gradlew jacocoTestReport'
            }
        }

        stage('Archive Jars') {
            steps {
                archiveArtifacts artifacts: '**/build/libs/*.jar', fingerprint: true
            }
        }

        stage('Archive JaCoCo Report') {
            steps {
                archiveArtifacts artifacts: '**/build/reports/jacoco/**', fingerprint: true
            }
        }
    }

    post {
        always {
            // 테스트 결과 수집
            junit '**/build/test-results/test/*.xml'
        }

        failure {
            echo '❌ Build, Test, or JaCoCo failed'
        }

        success {
            echo '✅ All modules built, tested, and JaCoCo report generated'
        }
    }
}
