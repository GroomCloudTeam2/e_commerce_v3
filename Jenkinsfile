pipeline {
    agent any

    environment {
        GRADLE_USER_HOME = "${WORKSPACE}/.gradle"
        GRADLE_OPTS = "-Dorg.gradle.daemon=false -Dorg.gradle.caching=true"
        CHANGED_MODULES = ''
    }

    options {
        timestamps()
        timeout(time: 1, unit: 'HOURS')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    sh 'git fetch origin main'
                    env.GIT_COMMIT_MSG = sh(
                        script: "git log -1 --pretty=%B",
                        returnStdout: true
                    ).trim()
                    echo "Commit message: ${env.GIT_COMMIT_MSG}"
                }
            }
        }

        stage('Detect changed modules') {
            steps {
                script {
                    try {
                        // merge-base로 정확한 기준점 찾기
                        def baseCommit = sh(
                            script: """
                                if git rev-parse origin/main >/dev/null 2>&1; then
                                    git merge-base origin/main HEAD
                                else
                                    echo "INITIAL_BUILD"
                                fi
                            """,
                            returnStdout: true
                        ).trim()

                        // 초기 빌드 처리
                        if (baseCommit == "INITIAL_BUILD") {
                            echo "🚀 초기 빌드 - 전체 모듈 빌드"
                            env.CHANGED_MODULES = 'user,cart,order,payment,product'
                            return
                        }

                        echo "📍 Base commit: ${baseCommit}"

                        // 변경된 파일 목록 가져오기
                        def diffFiles = sh(
                            script: "git diff --name-only ${baseCommit}..HEAD",
                            returnStdout: true
                        ).trim()

                        if (!diffFiles) {
                            echo "ℹ️ 변경된 파일 없음"
                            env.CHANGED_MODULES = ''
                            return
                        }

                        echo "📝 Changed files:\n${diffFiles}"

                        // 변경된 모듈 추출
                        def modules = [] as Set
                        diffFiles.split('\n').each { file ->
                            if (file.startsWith('service/')) {
                                def parts = file.split('/')
                                if (parts.size() >= 2 && parts[1]) {
                                    modules << parts[1]
                                }
                            }
                            // 루트 빌드 파일 변경 시
                            if (file in ['build.gradle', 'settings.gradle', 'gradle.properties']) {
                                echo "⚠️ 루트 빌드 파일 변경 감지 → 전체 빌드"
                                modules = ['user', 'cart', 'order', 'payment', 'product'] as Set
                            }
                        }

                        // common 모듈 변경 시 전체 빌드
                        if (modules.contains('common')) {
                            echo '⚠️ common 모듈 변경 감지 → 전체 서비스 빌드'
                            modules = ['user', 'cart', 'order', 'payment', 'product'] as Set
                        }

                        env.CHANGED_MODULES = modules.join(',')
                        echo "🧩 Final build modules: ${env.CHANGED_MODULES}"

                    } catch (Exception e) {
                        echo "❌ 변경 감지 실패: ${e.message}"
                        echo "🔄 안전을 위해 전체 빌드 수행"
                        env.CHANGED_MODULES = 'user,cart,order,payment,product'
                    }
                }
            }
        }

        stage('Test & Build') {
            when {
                expression { env.CHANGED_MODULES?.trim() }
            }
            steps {
                script {
                    def moduleList = env.CHANGED_MODULES.split(',')

                    // 모듈이 1개면 순차, 2개 이상이면 병렬 처리
                    if (moduleList.size() == 1) {
                        def module = moduleList[0]
                        echo "🚀 Building single module: ${module}"
                        sh """
                            ./gradlew :service:${module}:clean \
                                      :service:${module}:test \
                                      :service:${module}:bootJar \
                                      --parallel
                        """
                    } else {
                        echo "🚀 Building ${moduleList.size()} modules in parallel"
                        def builds = [:]

                        moduleList.each { module ->
                            builds[module] = {
                                stage("Build ${module}") {
                                    echo "🔨 Building module: ${module}"
                                    sh """
                                        ./gradlew :service:${module}:clean \
                                                  :service:${module}:test \
                                                  :service:${module}:bootJar \
                                                  --parallel
                                    """
                                }
                            }
                        }

                        parallel builds
                    }
                }
            }
        }

        stage('Code Quality') {
            when {
                expression { env.CHANGED_MODULES?.trim() }
            }
            steps {
                script {
                    env.CHANGED_MODULES.split(',').each { module ->
                        echo "📊 Running code quality checks for: ${module}"
                        // 필요시 checkstyle, spotbugs 등 추가
                        sh "./gradlew :service:${module}:check || true"
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                // 테스트 결과 수집
                if (env.CHANGED_MODULES?.trim()) {
                    env.CHANGED_MODULES.split(',').each { module ->
                        junit allowEmptyResults: true,
                             testResults: "service/${module}/build/test-results/**/*.xml"
                    }
                }

                // 빌드 아티팩트 보관
                archiveArtifacts artifacts: 'service/**/build/libs/*.jar',
                                allowEmptyArchive: true,
                                fingerprint: true

                // Gradle 캐시 정리 (선택적)
                // sh './gradlew clean --quiet || true'
            }
        }

        success {
            script {
                def moduleCount = env.CHANGED_MODULES?.trim() ?
                    env.CHANGED_MODULES.split(',').size() : 0
                echo """
                ✅ 빌드 성공!
                📦 변경된 모듈 수: ${moduleCount}
                🧩 모듈 목록: ${env.CHANGED_MODULES ?: '없음'}
                """
            }
        }

        failure {
            script {
                echo """
                ❌ 빌드 실패
                🧩 시도한 모듈: ${env.CHANGED_MODULES ?: '없음'}
                💡 로그를 확인하세요
                """
            }
        }

        unstable {
            echo '⚠️ 빌드는 성공했으나 테스트에 실패한 케이스가 있습니다'
        }
    }
}