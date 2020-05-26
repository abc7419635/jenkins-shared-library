/*
@Library("sharelib") _

pipeline {
    agent none
    parameters {
        booleanParam(name: 'Refresh', defaultValue: false, description: '')
        booleanParam(name: 'SkipP4Update', defaultValue: false, description: '')
        string(name: 'P4Credential', defaultValue: 'programmer', description: '')
        string(name: 'P4Workspace', defaultValue: 'RD_DailyBuild', description: '')
        choice(name: 'P4Stream', choices: ['//GD2ReDream/DailyBuild'], description: '')
        string(name: 'P4RootDir', defaultValue: 'D:\\RD_DailyBuild', description: '')
        string(name: 'BOTO_CONFIG', defaultValue: 'D:\\JenkinsRemoteRoot\\.boto', description: '')

        string(name: 'UNREAL_BUILD_DIR', defaultValue: 'E:\\ReDreamPackage', description: '')
        string(name: 'UNREAL_GAME_DIR', defaultValue: 'D:\\RD_DailyBuild\\Game\\ReDream\\ReDream.uproject', description: '')
        string(name: 'UNREAL_SOURCECODE_DIR', defaultValue: 'D:\\RD_DailyBuild\\Game', description: '')

        booleanParam(name: 'CLEARCOOK', defaultValue: false, description: '')
    }
    
    stages {
        stage('Init Parameters') {
            steps {
                echo 'Init Parameters'
            }
        }
    }
}

if(env.Refresh=='false')
{
    UnrealBuildScript()
}
*/

import java.text.SimpleDateFormat

def call(body) {
    node('RemoteBuildPC') {
        stage('Sync Perforce') {
            if(env.SkipP4Update=='false')
            {
                dir(env.P4RootDir) {
                    checkout perforce(credential: env.P4Credential,
                    populate: syncOnly(force: false, have: true, modtime: false, quiet: false, revert: false),
                    workspace: manualSpec(charset: 'utf8', name: env.P4Workspace, pinHost: true,
                    spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false, line: 'UNIX', locked: false, modtime: false, rmdir: true, serverID: '',
                    streamName: env.P4Stream, type: 'WRITABLE', view: '')))
                }
            }
            else
            {
                echo 'Skip Sync Perforce'
            }
        }

        stage('PreBuild') {
            bat '''
                rmdir /s/q %UNREAL_BUILD_DIR%
                md %UNREAL_BUILD_DIR%

                echo %P4_CHANGELIST% > "D:\\_BuildTools\\temp\\ContentVersion.txt"

                set P4USER=programmer
                set P4PASSWD=F5E023356D8072F0A3521F83E5678CE0
                set P4PORT=10.2.100.220:1001
                set P4CLIENT=RD_DailyBuild

                for /F %%i IN (D:\\_BuildTools\\temp\\ContentVersion.txt) DO (
                set ContentVersion=%%i
                )

                p4 edit -c default d:\\RD_DailyBuild\\Game\\ReDream\\Config\\DefaultGame.ini
                %UNREAL_SOURCECODE_DIR%\\Engine\\Binaries\\ThirdParty\\Python\\Win64\\python.exe D:\\_BuildTools\\Python\\configReaderV2.py %BUILD_ID% %ContentVersion% %SourceVersion%
                p4 submit -d "[AutoBuild] Auto Increase Version %BUILD_ID% ContentVer:%ContentVersion%" -f revertunchanged

                echo %BUILD_ID% > "D:\\_BuildTools\\temp\\BuildVersion.txt"
                '''
        }
    }
}