def call(body) {
    pipeline {
        agent none
        parameters {
                //string(name: 'PERSON', defaultValue: 'Mr Jenkins', description: 'Who should I say hello to?')
        
                //text(name: 'BIOGRAPHY', defaultValue: '', description: 'Enter some information about the person')
        
                //booleanParam(name: 'TOGGLE', defaultValue: true, description: 'Toggle this value')
        
                //choice(name: 'CHOICE', choices: ['One', 'Two', 'Three'], description: 'Pick something')
                //${params.CHOICE}
                //password(name: 'PASSWORD', defaultValue: 'SECRET', description: 'Enter a password')

                string(name: 'GameModelPath', defaultValue: 'D:\\RD_GameModel', description: 'GameModel Path')

                booleanParam(name: 'Deploy', defaultValue: false, description: 'deploy to staging')
        }
        
        stages {
            stage('Sync Perforce') {
                when {
                    environment name: 'CHOICE', value: 'One'
                }
                agent {
                    label 'ServerModelBuildPC'
                }
                steps {
                    echo "${params.GameModelPath}"
                    dir("${params.GameModelPath}") {
                        //checkout perforce(credential: 'programmer', populate: syncOnly(force: false, have: true, modtime: false, quiet: false, revert: false), workspace: manualSpec(charset: 'utf8', name: 'RD_DailyCCB', pinHost: true, spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false, line: 'UNIX', locked: false, modtime: false, rmdir: true, serverID: '', streamName: '//GD2ReDream/RD_DailyCCB', type: 'WRITABLE', view: '')))
                        checkout perforce(credential: 'programmer', populate: syncOnly(force: false, have: true, modtime: false, quiet: false, revert: false), workspace: manualSpec(charset: 'utf8', name: 'RD_GameModel', pinHost: true, spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false, line: 'UNIX', locked: false, modtime: false, rmdir: true, serverID: '', streamName: '//GD2ReDream/GameModel', type: 'WRITABLE', view: '')))
                    }
                }
            }
            stage('Build Services') {
                agent {
                    label 'ServerModelBuildPC'
                }
                steps {
                    dir("${params.GameModelPath}") {
                        bat '''
                        cd GameModel\\Model.Server
                        call Deployment\\Bot\\ClearLogs.bat
                        call Deployment\\DeployCore\\Instances\\ClearLogs.bat
                        "..\\Tools\\ExcelParser\\MSBuild\\15.0\\Bin\\MSBuild.exe" "GameModel.sln" -p:Configuration=Release -restore -t:rebuild
                        '''
                    }
                }
            }
            stage('CompressUpload') {
                agent {
                    label 'ServerModelBuildPC'
                }
                steps {
                    bat '''
                        set x=%date:~0,4%%date:~5,2%%date:~8,2%
                        IF "%time:~0,1%" == " " (
                            set y=0%time:~1,1%%time:~3,2%%time:~6,2%
                        )ELSE (
                            set y=%time:~0,2%%time:~3,2%%time:~6,2%
                        )

                        7z a %GameModelPath%\\GameModel\\DeploymentPack\\Release_%BUILD_NUMBER%_%x%_%y% %GameModelPath%\\GameModel\\Model.Server\\Deployment

                        set BOTO_CONFIG=D:\\JenkinsRemoteRoot\\.boto
                        gsutil cp %GameModelPath%\\GameModel\\DeploymentPack\\Release_%BUILD_NUMBER%_%x%_%y%.7z gs://server_model_release/
                        '''
                }
            }
        }
    }
}