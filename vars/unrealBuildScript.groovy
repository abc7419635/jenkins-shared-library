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

                script {
                    if(env.Refresh=='false') {
                        unrealBuildScript()
                    }
                }
            }
        }
    }

    post {
        failure {
            script {
                unrealBuildScript.buildfailure()
            }
        }
    }
}

*/

import java.text.SimpleDateFormat

def buildfailure() {
    println 'error error error error error'
}

def call(body) {
    node('RemoteBuildPC') {
        /*stage('Test') {
            error 'OK'
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

        stage('Increase Version') {
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
            bat '"%UNREAL_SOURCECODE_DIR%\\Engine\\Build\\BatchFiles\\Build.bat" "ReDreamEditor" Win64 Development -WarningsAsErrors %UNREAL_GAME_DIR% || python D:\\_BuildTools\\Python\\ReportFailure.py BuildEditor'
            bat 'python D:\\_BuildTools\\Python\\ReportSuccess.py BuildEditor'
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
            bat '"%UNREAL_SOURCECODE_DIR%\\Engine\\Build\\BatchFiles\\RunUAT.bat" BuildCookRun -project=%UNREAL_GAME_DIR% -noP4 -platform=Win64 -clientconfig=Development -cook -pak -build -stage -archive -archivedirectory=%UNREAL_BUILD_DIR% -utf8output -compressed -prereqs -iterate -AdditionalCookerOptions=-BUILDMACHINE || python D:\\_BuildTools\\Python\\ReportFailure.py WindowsClientDev'
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
                def date = new Date(Calendar.getInstance().getTimeInMillis() + (8 * 60 * 60 * 1000))
                def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
                def timestring = sdf.format(date)
                env.WindowsClientDevName = 'WindowsClientDev_' + env.P4Stream.substring(13) + '_' + readfilevar + '_' + timestring
                echo env.WindowsClientDevName
            }

            bat 'rename E:\\ReDreamPackage %WindowsClientDevName%'

            build job: 'RemoteBuildCompress', parameters: [string(name: 'DATAPATH', value: 'E:\\'+env.WindowsClientDevName),
            string(name: 'ZIPNAME', value: env.WindowsClientDevName)], wait: false

            bat 'python D:\\_BuildTools\\Python\\ReportSuccess.py WindowsClientDev'
        }

        stage('WindowsServerDev') {
            bat '"%UNREAL_SOURCECODE_DIR%\\Engine\\Build\\BatchFiles\\RunUAT.bat" BuildCookRun -project=%UNREAL_GAME_DIR% -noP4 -platform=Win64 -serverconfig=Development -cook -pak -build -stage -server -serverplatform=Win64 -noclient -archive -archivedirectory=%UNREAL_BUILD_DIR% -utf8output -compressed -prereqs -DUMPALLWARNINGS -iterate -AdditionalCookerOptions=-BUILDMACHINE || python D:\\_BuildTools\\Python\\ReportFailure.py WindowsServerDev'
            bat '''
                xcopy D:\\_BuildTools\\EAC\\_EACWinServer %UNREAL_BUILD_DIR%\\WindowsServer /s /e /y
                xcopy D:\\RD_DailyBuild\\Game\\ReDream\\Binaries\\WindowsDevelopmentConfig\\RDSetting.ini %UNREAL_BUILD_DIR%\\WindowsServer\\ReDream\\Saved\\Config\\WindowsServer\\ /s /e /y
                '''
            bat 'echo ReDream\\Binaries\\Win64\\ReDreamServer.exe /Game/Main/Maps/Scn01/MAP_Scn01_EA_BC -log networkprofiler=true > %UNREAL_BUILD_DIR%\\WindowsServer\\ReDreamServer.bat'
            dir('D:\\_BuildTools\\temp') {
                def readfilevar = readFile('BuildVersion.txt').replaceAll("\\s","")
                def date = new Date(Calendar.getInstance().getTimeInMillis() + (8 * 60 * 60 * 1000))
                def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
                def timestring = sdf.format(date)
                env.WindowsServerDevName = 'WindowsServerDev_' + env.P4Stream.substring(13) + '_' + readfilevar + '_' + timestring
                echo env.WindowsServerDevName
            }
            bat 'rename E:\\ReDreamPackage %WindowsServerDevName%'

            build job: 'RemoteBuildCompress', parameters: [string(name: 'DATAPATH', value: 'E:\\'+env.WindowsServerDevName),
            string(name: 'ZIPNAME', value: env.WindowsServerDevName)], wait: false

            bat 'python D:\\_BuildTools\\Python\\ReportSuccess.py WindowsServerDev'
        }

        stage('LinuxServerDev') {
            bat '"%UNREAL_SOURCECODE_DIR%\\Engine\\Build\\BatchFiles\\RunUAT.bat" BuildCookRun -project=%UNREAL_GAME_DIR% -noP4 -platform=Linux -serverconfig=Development -cook -pak -build -stage -server -serverplatform=Linux -noclient -archive -archivedirectory=%UNREAL_BUILD_DIR% -utf8output -compressed -prereqs -iterate -AdditionalCookerOptions=-BUILDMACHINE || python D:\\_BuildTools\\Python\\ReportFailure.py LinuxServerDev LinuxServerDev'
            bat '''
                xcopy D:\\_BuildTools\\EAC\\_EACLinuxServer %UNREAL_BUILD_DIR%\\LinuxServer /s /e /y
                xcopy D:\\RD_DailyBuild\\Game\\ReDream\\Binaries\\DevelopmentConfig\\RDSetting.ini %UNREAL_BUILD_DIR%\\LinuxServer\\ReDream\\Saved\\Config\\LinuxServer\\ /s /e /y
                '''
            dir('D:\\_BuildTools\\temp') {
                env.BuildVer = readFile('BuildVersion.txt').replaceAll("\\s","")
            }

            bat '''
                echo %BuildVer% > %UNREAL_BUILD_DIR%\\LinuxServer\\version.txt
                rmdir /Q/S \\\\10.2.11.61\\D\\Docker\\image\\launchserver\\LinuxServer
                xcopy %UNREAL_BUILD_DIR% \\\\10.2.11.61\\D\\Docker\\image\\launchserver\\ /s /e /y

                rmdir /Q/S \\\\10.2.11.122\\D\\_LinuxServerOld\\LinuxServer
                move \\\\10.2.11.122\\D\\_Launch\\LinuxServer \\\\10.2.11.122\\D\\_LinuxServerOld
                xcopy %UNREAL_BUILD_DIR% \\\\10.2.11.122\\D\\_Launch\\ /s /e /y
                copy /Y \\\\10.2.11.122\\D\\_Launch\\RDSetting.ini \\\\10.2.11.122\\D\\_Launch\\LinuxServer\\ReDream\\Saved\\Config\\LinuxServer\\
                '''


            dir('D:\\_BuildTools\\temp') {
                def readfilevar = readFile('BuildVersion.txt').replaceAll("\\s","")
                def date = new Date(Calendar.getInstance().getTimeInMillis() + (8 * 60 * 60 * 1000))
                def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
                def timestring = sdf.format(date)
                env.LinuxServerDevName = 'LinuxServerDev_' + env.P4Stream.substring(13) + '_' + readfilevar + '_' + timestring
                echo env.LinuxServerDevName
            }
            bat 'rename E:\\ReDreamPackage %LinuxServerDevName%'

            build job: 'RemoteBuildCompress', parameters: [
            string(name: 'DATAPATH', value: 'E:\\'+env.LinuxServerDevName),
            string(name: 'ZIPNAME', value: env.LinuxServerDevName+'.tar')], wait: false

            bat 'python D:\\_BuildTools\\Python\\ReportSuccess.py LinuxServerDev'
        }

        stage('WindowsClientShipping') {
            bat '"%UNREAL_SOURCECODE_DIR%\\Engine\\Build\\BatchFiles\\RunUAT.bat" BuildCookRun -project=%UNREAL_GAME_DIR% -noP4 -platform=Win64 -clientconfig=Shipping -cook -pak -build -stage -archive -archivedirectory=%UNREAL_BUILD_DIR% -utf8output -compressed -prereqs -iterate -AdditionalCookerOptions=-BUILDMACHINE || python D:\\_BuildTools\\Python\\ReportFailure.py WindowsClientShipping WindowsClientShipping'

            bat '''
                xcopy D:\\_BuildTools\\TrueSkyLib %UNREAL_BUILD_DIR%\\WindowsNoEditor\\Engine /s /e /y
                E:
                cd %UNREAL_BUILD_DIR%\\WindowsNoEditor
                del ReDream.exe
                cd ReDream\\Binaries\\Win64
                rename ReDream-Win64-Shipping.exe ReDream.exe

                xcopy D:\\_BuildTools\\EAC\\_EACClient %UNREAL_BUILD_DIR%\\WindowsNoEditor /s /e /y
                copy D:\\_BuildTools\\SteamSDK\\installscript.vdf %UNREAL_BUILD_DIR%\\WindowsNoEditor\\ /y
                '''
            
            bat '''
                D:
                cd D:\\_BuildTools\\EAC\\AntiCheatSDK\\Client\\HashTool
                eac_hashtool.exe
                '''

            dir('D:\\_BuildTools\\temp') {
                def readfilevar = readFile('BuildVersion.txt').replaceAll("\\s","")
                def date = new Date(Calendar.getInstance().getTimeInMillis() + (8 * 60 * 60 * 1000))
                def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
                def timestring = sdf.format(date)
                env.WindowsClientShippingName = 'WindowsClientShipping_' + env.P4Stream.substring(13) + '_' + readfilevar + '_' + timestring
                echo env.WindowsClientShippingName
            }
            bat 'rename E:\\ReDreamPackage %WindowsClientShippingName%'

            build job: 'RemoteBuildCompress', parameters: [
            string(name: 'DATAPATH', value: 'E:\\'+env.WindowsClientShippingName),
            string(name: 'ZIPNAME', value: env.WindowsClientShippingName)], wait: false

            bat 'python D:\\_BuildTools\\Python\\ReportSuccess.py WindowsClientShipping'
        }

        stage('LinuxServerShipping') {
            bat '"%UNREAL_SOURCECODE_DIR%\\Engine\\Build\\BatchFiles\\RunUAT.bat" BuildCookRun -project=%UNREAL_GAME_DIR% -noP4 -platform=Linux -serverconfig=Shipping -cook -pak -build -stage -server -serverplatform=Linux -noclient -archive -archivedirectory=%UNREAL_BUILD_DIR% -utf8output -compressed -prereqs -iterate -AdditionalCookerOptions=-BUILDMACHINE || python D:\\_BuildTools\\Python\\ReportFailure.py LinuxServerShipping LinuxServerShipping'

            bat '''
                xcopy D:\\_BuildTools\\EAC\\_EACLinuxServer %UNREAL_BUILD_DIR%\\LinuxServer /s /e /y
                xcopy D:\\RD_DailyBuild\\Game\\ReDream\\Binaries\\DevelopmentConfig\\RDSetting.ini %UNREAL_BUILD_DIR%\\LinuxServer\\ReDream\\Saved\\Config\\LinuxServer\\ /s /e /y
                '''

            dir('D:\\_BuildTools\\temp') {
                def readfilevar = readFile('BuildVersion.txt').replaceAll("\\s","")
                def date = new Date(Calendar.getInstance().getTimeInMillis() + (8 * 60 * 60 * 1000))
                def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
                def timestring = sdf.format(date)
                env.LinuxServerShippingName = 'LinuxServerShipping_' + env.P4Stream.substring(13) + '_' + readfilevar + '_' + timestring
                echo env.LinuxServerShippingName
            }
            bat 'rename E:\\ReDreamPackage %LinuxServerShippingName%'

            build job: 'RemoteBuildCompress', parameters: [
            string(name: 'DATAPATH', value: 'E:\\'+env.LinuxServerShippingName),
            string(name: 'ZIPNAME', value: env.LinuxServerShippingName+'.tar')], wait: false

            bat 'python D:\\_BuildTools\\Python\\ReportSuccess.py LinuxServerShipping'
        }
    }
}