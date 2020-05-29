/*
@Library("sharelib") _

pipeline {
    agent none
    parameters {
        booleanParam(name: 'Refresh', defaultValue: false, description: '')
        booleanParam(name: 'SkipP4Update', defaultValue: false, description: '')
        string(name: 'P4Credential', defaultValue: 'programmer', description: '')
        string(name: 'P4WorkspaceName', defaultValue: 'RD_DailyMain', description: '')
        string(name: 'P4Stream', defaultValue: '//GD2ReDream/DailyBuild', description: '')
        string(name: 'P4RootDir', defaultValue: 'F:\\RD_DailyMain', description: '')
        string(name: 'BOTO_CONFIG', defaultValue: 'D:\\JenkinsRemoteRoot\\.boto', description: '')

        string(name: 'UNREAL_PROJECT_NAME', defaultValue: 'ReDream', description: '')
        string(name: 'UNREAL_BUILD_DIR', defaultValue: 'E:\\', description: '')

        booleanParam(name: 'CLEARCOOK', defaultValue: false, description: '')

        booleanParam(name: 'Build_WIN_CLIENT_DEV', defaultValue: true, description: '')
        booleanParam(name: 'Build_LINUX_SERVER_DEV', defaultValue: true, description: '')
        booleanParam(name: 'Build_WIN_SERVER_DEV', defaultValue: true, description: '')
        booleanParam(name: 'Build_WIN_CLIENT_SHIPPING', defaultValue: true, description: '')
        booleanParam(name: 'Build_LINUX_SERVER_SHIPPING', defaultValue: true, description: '')
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

    //post {
    //    failure {
    //        script {
    //            unrealBuildScript.buildfailure()
    //        }
    //    }
    //}
}
*/

import java.text.SimpleDateFormat

def buildfailure() {
    println 'error error error error error'
}

def call(body) {
    node('RemoteBuildPC') {
        /*stage('Test') {
            
        }
        return;*/

        env.UNREAL_GAME_PROJECT = env.P4RootDir + '\\Game\\' + env.UNREAL_PROJECT_NAME + '\\' + env.UNREAL_PROJECT_NAME + '.uproject'
        env.UNREAL_SOURCE_ENGINE_DIR = env.P4RootDir + '\\Game\\Engine'
        env.UNREAL_COOK_LOG_PATH = env.UNREAL_SOURCE_ENGINE_DIR + '\\Programs\\AutomationTool\\Saved\\Logs\\Log.txt'
        env.UNREAL_BUILD_WORKDIR = env.UNREAL_BUILD_DIR + '\\' + env.UNREAL_PROJECT_NAME + '_BuildingDir'
        echo env.UNREAL_GAME_PROJECT
        echo env.UNREAL_SOURCE_ENGINE_DIR
        echo env.UNREAL_COOK_LOG_PATH
        echo env.UNREAL_BUILD_WORKDIR

        stage('Sync Perforce') {
            if(env.SkipP4Update=='false') {
                dir(env.P4RootDir) {
                    checkout perforce(credential: env.P4Credential,
                    populate: syncOnly(force: false, have: true, modtime: false, quiet: false, revert: false),
                    workspace: manualSpec(charset: 'utf8', name: env.P4WorkspaceName, pinHost: true,
                    spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false, line: 'UNIX', locked: false, modtime: false, rmdir: true, serverID: '',
                    streamName: env.P4Stream, type: 'WRITABLE', view: '')))
                }
            }
            else {
                echo 'Skip Sync Perforce'
            }
        }

        stage('Increase Version') {
            if(env.SkipP4Update=='false') {
                bat '''
                    rmdir /s/q %UNREAL_BUILD_WORKDIR%
                    md %UNREAL_BUILD_WORKDIR%

                    echo %P4_CHANGELIST% > "D:\\_BuildTools\\temp\\ContentVersion.txt"

                    set P4USER=%P4_USER%
                    set P4PASSWD=%P4_TICKET%
                    set P4PORT=%P4_PORT%
                    set P4CLIENT=%P4_CLIENT%

                    for /F %%i IN (D:\\_BuildTools\\temp\\ContentVersion.txt) DO (
                    set ContentVersion=%%i
                    )

                    set CheckOutFile=%P4_ROOT%\\Game\\ReDream\\Config\\DefaultGame.ini
                    p4 edit -c default %CheckOutFile%
                    %UNREAL_SOURCE_ENGINE_DIR%\\Binaries\\ThirdParty\\Python\\Win64\\python.exe D:\\_BuildTools\\Python\\configReaderV3.py %BUILD_ID% %ContentVersion% %CheckOutFile%
                    p4 submit -d "[AutoBuild] Auto Increase Version %BUILD_ID% ContentVer:%ContentVersion%" -f revertunchanged

                    echo %BUILD_ID% > "D:\\_BuildTools\\temp\\BuildVersion.txt"
                    '''
            }
            else {
                echo 'Skip Sync Perforce'
            }
        }

        stage('Build Editor') {
            if(env.SkipP4Update=='false') {
                bat '"%UNREAL_SOURCE_ENGINE_DIR%\\Build\\BatchFiles\\Build.bat" "ReDreamEditor" Win64 Development -WarningsAsErrors %UNREAL_GAME_PROJECT% || python D:\\_BuildTools\\Python\\ReportFailure.py BuildEditor'
                bat 'python D:\\_BuildTools\\Python\\ReportSuccess.py BuildEditor'
            }
        }

        stage('Cook Content') {
            if(env.SkipP4Update=='false') {
            bat '''
                if "%CLEARCOOK%"=="true" (rmdir /s/q %P4RootDir%\\Game\\ReDream\\Saved\\Cooked)

                %UNREAL_SOURCE_ENGINE_DIR%\\Binaries\\Win64\\UE4Editor-Cmd.exe %UNREAL_GAME_PROJECT% -run=Cook  -TargetPlatform=WindowsNoEditor+WindowsServer+LinuxServer -fileopenlog -unversioned -BUILDMACHINE -stdout -CrashForUAT -unattended -NoLogTimes -UTF8Output -iterate -iterateshash -abslog=%UNREAL_COOK_LOG_PATH% 

                python D:\\_BuildTools\\Python\\DiscordNotifyCooksummaryV2.py %UNREAL_COOK_LOG_PATH% || exit 1
                '''
            }
        }

        stage('ExportDataTable') {
            if(env.SkipP4Update=='false') {
                bat '''
                    set P4USER=%P4_USER%
                    set P4PASSWD=%P4_TICKET%
                    set P4PORT=%P4_PORT%
                    set P4CLIENT=%P4_CLIENT%

                    p4 edit -c default %P4_ROOT%\\GameModel\\Model.Server\\ServerData\\MatchModeData.json
                    p4 edit -c default %P4_ROOT%\\GameModel\\Model.Server\\ServerData\\DataT_Zone.json

                    call %UNREAL_SOURCE_ENGINE_DIR%\\Binaries\\Win64\\UE4Editor-Cmd.exe %UNREAL_GAME_PROJECT% -run=ExportDataTable -datapath=/Game/Main/Gameplay/GameData/DataT_MatchMode -outpath=%P4_ROOT%/GameModel/Model.Server/ServerData/MatchModeData.json
                    call %UNREAL_SOURCE_ENGINE_DIR%\\Binaries\\Win64\\UE4Editor-Cmd.exe %UNREAL_GAME_PROJECT% -run=ExportDataTable -datapath=/Game/Main/Gameplay/GameData/DataT_Zone -outpath=%P4_ROOT%/GameModel/Model.Server/ServerData/DataT_Zone.json

                    p4 revert -a -c default
                    p4 submit -d "[AutoBuild] update MatchModeData.json DataT_Zone.json" -f revertunchanged || exit 0
                    '''

                /*build job: 'RD_GameModelServiceScript', parameters: [
                string(name: 'P4Credential', value: env.P4Credential),
                string(name: 'P4WorkspaceName', value: env.P4WorkspaceName),
                string(name: 'P4Stream', value: env.P4Stream),
                string(name: 'P4RootDir', value: env.P4RootDir),
                string(name: 'BOTO_CONFIG', value: env.BOTO_CONFIG),
                booleanParam(name: 'DeployToStaging', value: true)], wait: false*/

                uild job: 'RD_GameModelServiceScript', parameters: [
                booleanParam(name: 'DeployToStaging', value: true)], wait: false

            }
            else {
                echo 'Skip Sync Perforce'
            }
        }

        stage('WindowsClient Dev') {
            if(env.Build_WIN_CLIENT_DEV=='true')
            {
                bat '"%UNREAL_SOURCE_ENGINE_DIR%\\Build\\BatchFiles\\RunUAT.bat" BuildCookRun -project=%UNREAL_GAME_PROJECT% -noP4 -platform=Win64 -clientconfig=Development -cook -pak -build -stage -archive -archivedirectory=%UNREAL_BUILD_WORKDIR% -utf8output -compressed -prereqs -iterate -AdditionalCookerOptions=-BUILDMACHINE || python D:\\_BuildTools\\Python\\ReportFailure.py WindowsClientDev'
                bat '''
                    rename %UNREAL_BUILD_WORKDIR%\\WindowsNoEditor\\ReDream.exe ReDream.bak
                    xcopy D:\\_BuildTools\\TrueSkyLib %UNREAL_BUILD_WORKDIR%\\WindowsNoEditor\\Engine /s /e /y
                    xcopy D:\\_BuildTools\\EAC\\_EACClient %UNREAL_BUILD_WORKDIR%\\WindowsNoEditor /s /e /y
                    xcopy %P4RootDir%\\Game\\ReDream\\Binaries\\DevelopmentConfig %UNREAL_BUILD_WORKDIR%\\WindowsNoEditor\\ReDream\\Saved\\Config\\WindowsNoEditor\\ /s /e /y
                    copy D:\\_BuildTools\\SteamSDK\\installscript.vdf %UNREAL_BUILD_WORKDIR%\\WindowsNoEditor\\ /y
                    '''

                bat '''
                    D:
                    cd D:\\_BuildTools\\EAC\\AntiCheatSDK\\Client\\HashTool
                    eac_hashtool.exe -working_dir %UNREAL_BUILD_WORKDIR%\\WindowsNoEditor\\
                    '''

                dir('D:\\_BuildTools\\temp') {
                    def readfilevar = readFile('BuildVersion.txt').replaceAll("\\s","")
                    def date = new Date(Calendar.getInstance().getTimeInMillis() + (8 * 60 * 60 * 1000))
                    def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
                    def timestring = sdf.format(date)
                    env.WindowsClientDevName = 'WindowsClientDev_' + env.P4Stream.substring(13) + '_' + readfilevar + '_' + timestring
                    echo env.WindowsClientDevName
                }

                bat 'rename %UNREAL_BUILD_WORKDIR% %WindowsClientDevName%'

                build job: 'RemoteBuildCompress', parameters: [string(name: 'DATAPATH', value: '%UNREAL_BUILD_DIR%'+env.WindowsClientDevName),
                string(name: 'ZIPNAME', value: env.WindowsClientDevName)], wait: false

                build job: 'SteamDeploy', parameters: [string(name: 'GAME_PATH', value: '%UNREAL_BUILD_DIR%'+env.WindowsClientDevName),
                string(name: 'ALIVE_BRANCH', value: 'development')], wait: false

                bat 'python D:\\_BuildTools\\Python\\ReportSuccess.py WindowsClientDev'
            }
        }

        stage('LinuxServer Dev') {
            if(env.Build_LINUX_SERVER_DEV=='true')
            {
                bat '"%UNREAL_SOURCE_ENGINE_DIR%\\Build\\BatchFiles\\RunUAT.bat" BuildCookRun -project=%UNREAL_GAME_PROJECT% -noP4 -platform=Linux -serverconfig=Development -cook -pak -build -stage -server -serverplatform=Linux -noclient -archive -archivedirectory=%UNREAL_BUILD_WORKDIR% -utf8output -compressed -prereqs -iterate -AdditionalCookerOptions=-BUILDMACHINE || python D:\\_BuildTools\\Python\\ReportFailure.py LinuxServerDev LinuxServerDev'
                bat '''
                    xcopy D:\\_BuildTools\\EAC\\_EACLinuxServer %UNREAL_BUILD_WORKDIR%\\LinuxServer /s /e /y
                    xcopy %P4RootDir%\\Game\\ReDream\\Binaries\\DevelopmentConfig\\RDSetting.ini %UNREAL_BUILD_WORKDIR%\\LinuxServer\\ReDream\\Saved\\Config\\LinuxServer\\ /s /e /y
                    '''
                dir('D:\\_BuildTools\\temp') {
                    env.BuildVer = readFile('BuildVersion.txt').replaceAll("\\s","")
                }

                bat '''
                    echo %BuildVer% > %UNREAL_BUILD_WORKDIR%\\LinuxServer\\version.txt
                    rmdir /Q/S \\\\10.2.11.61\\D\\Docker\\image\\launchserver\\LinuxServer
                    xcopy %UNREAL_BUILD_WORKDIR% \\\\10.2.11.61\\D\\Docker\\image\\launchserver\\ /s /e /y

                    rmdir /Q/S \\\\10.2.11.122\\D\\_LinuxServerOld\\LinuxServer
                    move \\\\10.2.11.122\\D\\_Launch\\LinuxServer \\\\10.2.11.122\\D\\_LinuxServerOld
                    xcopy %UNREAL_BUILD_WORKDIR% \\\\10.2.11.122\\D\\_Launch\\ /s /e /y
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
                bat 'rename %UNREAL_BUILD_WORKDIR% %LinuxServerDevName%'

                build job: 'RemoteBuildCompress', parameters: [
                string(name: 'DATAPATH', value: '%UNREAL_BUILD_DIR%'+env.LinuxServerDevName),
                string(name: 'ZIPNAME', value: env.LinuxServerDevName+'.tar')], wait: false

                /*build job: 'RD_LaunchServiceScript', parameters: [
                string(name: 'P4Credential', value: env.P4Credential),
                string(name: 'P4WorkspaceName', value: env.P4WorkspaceName),
                string(name: 'P4Stream', value: env.P4Stream),
                string(name: 'P4RootDir', value: env.P4RootDir),
                string(name: 'BOTO_CONFIG', value: env.BOTO_CONFIG),
                booleanParam(name: 'DeployToStaging', value: true),
                booleanParam(name: 'DeployToLocal', value: true)], wait: false*/

                build job: 'RD_LaunchServiceScript', parameters: [
                booleanParam(name: 'DeployToStaging', value: true),
                booleanParam(name: 'DeployToLocal', value: true)], wait: false

                bat 'python D:\\_BuildTools\\Python\\ReportSuccess.py LinuxServerDev'
            }
        }

        stage('WindowsServer Dev') {
            if(env.Build_WIN_SERVER_DEV=='true')
            {
                bat '"%UNREAL_SOURCE_ENGINE_DIR%\\Build\\BatchFiles\\RunUAT.bat" BuildCookRun -project=%UNREAL_GAME_PROJECT% -noP4 -platform=Win64 -serverconfig=Development -cook -pak -build -stage -server -serverplatform=Win64 -noclient -archive -archivedirectory=%UNREAL_BUILD_WORKDIR% -utf8output -compressed -prereqs -DUMPALLWARNINGS -iterate -AdditionalCookerOptions=-BUILDMACHINE || python D:\\_BuildTools\\Python\\ReportFailure.py WindowsServerDev'
                bat '''
                    xcopy D:\\_BuildTools\\EAC\\_EACWinServer %UNREAL_BUILD_WORKDIR%\\WindowsServer /s /e /y
                    xcopy %P4RootDir%\\Game\\ReDream\\Binaries\\WindowsDevelopmentConfig\\RDSetting.ini %UNREAL_BUILD_WORKDIR%\\WindowsServer\\ReDream\\Saved\\Config\\WindowsServer\\ /s /e /y
                    '''
                bat 'echo ReDream\\Binaries\\Win64\\ReDreamServer.exe /Game/Main/Maps/Scn01/MAP_Scn01_EA_BC -log networkprofiler=true > %UNREAL_BUILD_WORKDIR%\\WindowsServer\\ReDreamServer.bat'
                dir('D:\\_BuildTools\\temp') {
                    def readfilevar = readFile('BuildVersion.txt').replaceAll("\\s","")
                    def date = new Date(Calendar.getInstance().getTimeInMillis() + (8 * 60 * 60 * 1000))
                    def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
                    def timestring = sdf.format(date)
                    env.WindowsServerDevName = 'WindowsServerDev_' + env.P4Stream.substring(13) + '_' + readfilevar + '_' + timestring
                    echo env.WindowsServerDevName
                }
                bat 'rename %UNREAL_BUILD_WORKDIR% %WindowsServerDevName%'

                build job: 'RemoteBuildCompress', parameters: [string(name: 'DATAPATH', value: '%UNREAL_BUILD_DIR%'+env.WindowsServerDevName),
                string(name: 'ZIPNAME', value: env.WindowsServerDevName)], wait: false

                bat 'python D:\\_BuildTools\\Python\\ReportSuccess.py WindowsServerDev'
            }
        }

        stage('WindowsClient Shipping') {
            if(env.Build_WIN_CLIENT_SHIPPING=='true')
            {
                bat '"%UNREAL_SOURCE_ENGINE_DIR%\\Build\\BatchFiles\\RunUAT.bat" BuildCookRun -project=%UNREAL_GAME_PROJECT% -noP4 -platform=Win64 -clientconfig=Shipping -cook -pak -build -stage -archive -archivedirectory=%UNREAL_BUILD_WORKDIR% -utf8output -compressed -prereqs -iterate -AdditionalCookerOptions=-BUILDMACHINE || python D:\\_BuildTools\\Python\\ReportFailure.py WindowsClientShipping WindowsClientShipping'

                bat '''
                    xcopy D:\\_BuildTools\\TrueSkyLib %UNREAL_BUILD_WORKDIR%\\WindowsNoEditor\\Engine /s /e /y
                    
                    del %UNREAL_BUILD_WORKDIR%\\WindowsNoEditor\\ReDream.exe
                    rename %UNREAL_BUILD_WORKDIR%\\WindowsNoEditor\\ReDream\\Binaries\\Win64\\ReDream-Win64-Shipping.exe ReDream.exe

                    xcopy D:\\_BuildTools\\EAC\\_EACClient %UNREAL_BUILD_WORKDIR%\\WindowsNoEditor /s /e /y
                    copy D:\\_BuildTools\\SteamSDK\\installscript.vdf %UNREAL_BUILD_WORKDIR%\\WindowsNoEditor\\ /y
                    '''
                
                bat '''
                    D:
                    cd D:\\_BuildTools\\EAC\\AntiCheatSDK\\Client\\HashTool
                    eac_hashtool.exe -working_dir %UNREAL_BUILD_WORKDIR%\\WindowsNoEditor\\
                    '''

                dir('D:\\_BuildTools\\temp') {
                    def readfilevar = readFile('BuildVersion.txt').replaceAll("\\s","")
                    def date = new Date(Calendar.getInstance().getTimeInMillis() + (8 * 60 * 60 * 1000))
                    def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
                    def timestring = sdf.format(date)
                    env.WindowsClientShippingName = 'WindowsClientShipping_' + env.P4Stream.substring(13) + '_' + readfilevar + '_' + timestring
                    echo env.WindowsClientShippingName
                }
                bat 'rename %UNREAL_BUILD_WORKDIR% %WindowsClientShippingName%'

                build job: 'RemoteBuildCompress', parameters: [
                string(name: 'DATAPATH', value: '%UNREAL_BUILD_DIR%'+env.WindowsClientShippingName),
                string(name: 'ZIPNAME', value: env.WindowsClientShippingName)], wait: false

                build job: 'SteamDeploy', parameters: [string(name: 'GAME_PATH', value: '%UNREAL_BUILD_DIR%'+env.WindowsClientDevName),
                string(name: 'ALIVE_BRANCH', value: 'shipping')], wait: false

                bat 'python D:\\_BuildTools\\Python\\ReportSuccess.py WindowsClientShipping'
            }
        }

        stage('LinuxServer Shipping') {
            if(env.Build_LINUX_SERVER_SHIPPING=='true')
            {
                bat '"%UNREAL_SOURCE_ENGINE_DIR%\\Build\\BatchFiles\\RunUAT.bat" BuildCookRun -project=%UNREAL_GAME_PROJECT% -noP4 -platform=Linux -serverconfig=Shipping -cook -pak -build -stage -server -serverplatform=Linux -noclient -archive -archivedirectory=%UNREAL_BUILD_WORKDIR% -utf8output -compressed -prereqs -iterate -AdditionalCookerOptions=-BUILDMACHINE || python D:\\_BuildTools\\Python\\ReportFailure.py LinuxServerShipping LinuxServerShipping'

                bat '''
                    xcopy D:\\_BuildTools\\EAC\\_EACLinuxServer %UNREAL_BUILD_WORKDIR%\\LinuxServer /s /e /y
                    xcopy %P4RootDir%\\Game\\ReDream\\Binaries\\DevelopmentConfig\\RDSetting.ini %UNREAL_BUILD_WORKDIR%\\LinuxServer\\ReDream\\Saved\\Config\\LinuxServer\\ /s /e /y
                    '''

                dir('D:\\_BuildTools\\temp') {
                    def readfilevar = readFile('BuildVersion.txt').replaceAll("\\s","")
                    def date = new Date(Calendar.getInstance().getTimeInMillis() + (8 * 60 * 60 * 1000))
                    def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
                    def timestring = sdf.format(date)
                    env.LinuxServerShippingName = 'LinuxServerShipping_' + env.P4Stream.substring(13) + '_' + readfilevar + '_' + timestring
                    echo env.LinuxServerShippingName
                }
                bat 'rename %UNREAL_BUILD_WORKDIR% %LinuxServerShippingName%'

                build job: 'RemoteBuildCompress', parameters: [
                string(name: 'DATAPATH', value: '%UNREAL_BUILD_DIR%'+env.LinuxServerShippingName),
                string(name: 'ZIPNAME', value: env.LinuxServerShippingName+'.tar')], wait: false

                bat 'python D:\\_BuildTools\\Python\\ReportSuccess.py LinuxServerShipping'
            }
        }
    }
}