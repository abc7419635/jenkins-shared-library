def call(body) {
    pipeline {
        agent none
        parameters {
                string(name: 'PERSON', defaultValue: 'Mr Jenkins', description: 'Who should I say hello to?')
        
                text(name: 'BIOGRAPHY', defaultValue: '', description: 'Enter some information about the person')
        
                booleanParam(name: 'TOGGLE', defaultValue: true, description: 'Toggle this value')
        
                choice(name: 'CHOICE', choices: ['One', 'Two', 'Three'], description: 'Pick something')
                //${params.CHOICE}
                password(name: 'PASSWORD', defaultValue: 'SECRET', description: 'Enter a password')

                string(name: 'GameModelPath', defaultValue: 'D:\\RD_GameModel', description: 'GameModel Path')
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
                    echo ${params.GameModelPath}
                    dir(${params.GameModelPath}) {
                        //checkout perforce(credential: 'programmer', populate: syncOnly(force: false, have: true, modtime: false, quiet: false, revert: false), workspace: manualSpec(charset: 'utf8', name: 'RD_DailyCCB', pinHost: true, spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false, line: 'UNIX', locked: false, modtime: false, rmdir: true, serverID: '', streamName: '//GD2ReDream/RD_DailyCCB', type: 'WRITABLE', view: '')))
                        checkout perforce(credential: 'programmer', populate: syncOnly(force: false, have: true, modtime: false, quiet: false, revert: false), workspace: manualSpec(charset: 'utf8', name: 'RD_GameModel', pinHost: true, spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false, line: 'UNIX', locked: false, modtime: false, rmdir: true, serverID: '', streamName: '//GD2ReDream/GameModel', type: 'WRITABLE', view: '')))
                    }
                }
            }
        }
    }
}