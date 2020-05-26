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
        /*stage('TestStage') {
            
        }
        return;*/

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
            if(env.SkipP4Update=='false')
            {
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
            else
            {
                echo 'Skip Sync Perforce'
            }
        }

        stage('Build Editor') {
            bat '"%UNREAL_SOURCECODE_DIR%\\Engine\\Build\\BatchFiles\\Build.bat" "ReDreamEditor" Win64 Development -WarningsAsErrors %UNREAL_GAME_DIR% || python D:\\_BuildTools\\Python\\ReportFailure.py %JOB_NAME%'
        }

        stage('Cook Content') {
            bat '''
                if "%CLEARCOOK%"=="true" (rmdir /s/q D:\\RD_DailyBuild\\Game\\ReDream\\Saved\\Cooked)

                %UNREAL_SOURCECODE_DIR%\\Engine\\Binaries\\Win64\\UE4Editor-Cmd.exe %UNREAL_GAME_DIR% -run=Cook  -TargetPlatform=WindowsNoEditor+WindowsServer+LinuxServer -fileopenlog -unversioned -BUILDMACHINE -stdout -CrashForUAT -unattended -NoLogTimes -UTF8Output -iterate -iterateshash -abslog=%UNREAL_SOURCECODE_DIR%\\Engine\\Programs\\AutomationTool\\Saved\\Logs\\Log.txt 

                python D:\\_BuildTools\\Python\\DiscordNotifyCooksummary.py || exit 1
                '''
        }

        stage('ExportDataTable') {
            bat '''
                set P4USER=programmer
                set P4PASSWD=F5E023356D8072F0A3521F83E5678CE0
                set P4PORT=10.2.100.220:1001
                set P4CLIENT=RD_DailyBuild

                p4 edit -c default d:\\RD_DailyBuild\\GameModel\\Model.Server\\ServerData\\MatchModeData.json
                p4 edit -c default d:\\RD_DailyBuild\\GameModel\\Model.Server\\ServerData\\DataT_Zone.json

                call %UNREAL_SOURCECODE_DIR%\\Engine\\Binaries\\Win64\\UE4Editor-Cmd.exe %UNREAL_GAME_DIR% -run=ExportDataTable -datapath=/Game/Main/Gameplay/GameData/DataT_MatchMode -outpath=D:/RD_DailyBuild/GameModel/Model.Server/ServerData/MatchModeData.json
                call %UNREAL_SOURCECODE_DIR%\\Engine\\Binaries\\Win64\\UE4Editor-Cmd.exe %UNREAL_GAME_DIR% -run=ExportDataTable -datapath=/Game/Main/Gameplay/GameData/DataT_Zone -outpath=D:/RD_DailyBuild/GameModel/Model.Server/ServerData/DataT_Zone.json

                p4 submit -d "[AutoBuild] update MatchModeData.json DataT_Zone.json" -f revertunchanged || exit 0
                '''
            
            build job: 'RD_GameModelServiceScript', parameters: [booleanParam(name: 'DeployToStaging', value: true)], wait: false
        }

        stage('WindowsClientDev') {
            bat '"%UNREAL_SOURCECODE_DIR%\\Engine\\Build\\BatchFiles\\RunUAT.bat" BuildCookRun -project=%UNREAL_GAME_DIR% -noP4 -platform=Win64 -clientconfig=Development -cook -pak -build -stage -archive -archivedirectory=%UNREAL_BUILD_DIR% -utf8output -compressed -prereqs -iterate -AdditionalCookerOptions=-BUILDMACHINE || python D:\\_BuildTools\\Python\\ReportFailure.py DevWindowsClient'
            bat '''
                xcopy D:\\_BuildTools\\TrueSkyLib %UNREAL_BUILD_DIR%\\WindowsNoEditor\\Engine /s /e /y
                E:
                cd %UNREAL_BUILD_DIR%\\WindowsNoEditor
                rename ReDream.exe ReDream.bak
                xcopy D:\\_BuildTools\\EAC\\_EACClient %UNREAL_BUILD_DIR%\\WindowsNoEditor /s /e /y
                xcopy D:\\RD_DailyBuild\\Game\\ReDream\\Binaries\\DevelopmentConfig\\RDSetting.ini %UNREAL_BUILD_DIR%\\WindowsNoEditor\\ReDream\\Saved\\Config\\WindowsNoEditor\\ /s /e /y
                xcopy D:\\RD_DailyBuild\\Game\\ReDream\\Binaries\\DevelopmentConfig\\Input.ini %UNREAL_BUILD_DIR%\\WindowsNoEditor\\ReDream\\Saved\\Config\\WindowsNoEditor\\ /s /e /y
                xcopy D:\\RD_DailyBuild\\Game\\ReDream\\Binaries\\DevelopmentConfig\\Engine.ini %UNREAL_BUILD_DIR%\\WindowsNoEditor\\ReDream\\Saved\\Config\\WindowsNoEditor\\ /s /e /y
                copy D:\\_BuildTools\\SteamSDK\\installscript.vdf %UNREAL_BUILD_DIR%\\WindowsNoEditor\\ /y
                '''

            bat '''
                D:
                cd D:\\_BuildTools\\EAC\\AntiCheatSDK\\Client\\HashTool
                eac_hashtool.exe
                '''

            dir('D:\\_BuildTools\\temp') {
                def readfilevar = readFile('BuildVersion.txt').replaceAll("\\s","")
                def date = new Date()
                def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
                def timestring = sdf.format(date)
                env.WindowsClientDevName = 'WindowsClientDev_' + env.P4Stream.substring(13) + '_' + readfilevar + '_' + timestring
                echo env.WindowsClientDevName
            }

            bat '''
                rename E:\\ReDreamPackage %WindowsClientDevName%
                '''

            build job: 'RemoteBuildCompress', parameters: [string(name: 'DATAPATH', value: 'E:\\'+env.WindowsClientDevName),
            string(name: 'ZIPNAME', value: env.WindowsClientDevName)], wait: false
        }
    }
}