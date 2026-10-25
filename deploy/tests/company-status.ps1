#requires -Version 7.3
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot '../CompanyStatus.psm1') -Force
$cases = 0
function Assert-True([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message }; $script:cases++ }
$fixturePassword = 'disposable-database-secret-' + ('x' * 40)
$fixtureJwt = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('test-only-jwt-secret-of-32-bytes!!'))
$base = @{
    name='invault-check-a'
    services=@{
        database=@{volumes=@(@{type='volume';source='mysql-data';target='/var/lib/mysql'});environment=@{MYSQL_DATABASE='invault_check_a';MYSQL_USER='iv_check_a';MYSQL_PASSWORD=$fixturePassword;MYSQL_ROOT_PASSWORD=('root-test-secret-' + ('y'*40))}}
        backend=@{environment=@{DB_HOST='database';DB_NAME='invault_check_a';DB_USERNAME='iv_check_a';DB_PASSWORD=$fixturePassword;INVAULT_JWT_SECRET=$fixtureJwt;
            INVAULT_CORS_ALLOWED_ORIGINS='https://a.example.test';INVAULT_REALTIME_ALLOWED_ORIGINS='https://a.example.test';INVAULT_JWT_ISSUER='https://a.example.test';SPRING_PROFILES_ACTIVE='prod';INVAULT_BOOTSTRAP_ADMIN_ENABLED='true'}}
        frontend=@{environment=@{}}
        gateway=@{environment=@{APP_DOMAIN='a.example.test'};ports=@(@{published='80';target=80},@{published='443';target=443})}
    }
    networks=@{data=@{name='invault-check-a_data';internal=$true};edge=@{name='invault-check-a_edge'}}
    volumes=@{'mysql-data'=@{name='invault-check-a_mysql-data'}}
}
$baseJson = $base | ConvertTo-Json -Depth 20
function New-Model { return ($baseJson | ConvertFrom-Json -AsHashtable) }
function Read-ConfigReport($Model) { Get-InVaultCompanyConfigurationStatus -CompanyId check-a -Model $Model }
function New-Containers($Model) {
    foreach ($service in @('database','backend','frontend','gateway')) {
        $networks=@{}
        if ($service -in @('database','backend')) { $networks['invault-check-a_data']=@{} }
        if ($service -ne 'database') { $networks['invault-check-a_edge']=@{} }
        $mounts=if ($service -eq 'database') { @(@{Type='volume';Destination='/var/lib/mysql';Name='invault-check-a_mysql-data'}) } else { @() }
        @{
            Id=('a'*64);Image=('sha256:' + ('b'*64));State=@{Running=$true;Health=@{Status='healthy'}}
            Config=@{Labels=@{'com.docker.compose.project'='invault-check-a';'com.docker.compose.service'=$service};Env=@($Model.services[$service].environment.GetEnumerator() | ForEach-Object {"$($_.Key)=$($_.Value)"})}
            HostConfig=@{PortBindings=@{}};NetworkSettings=@{Networks=$networks};Mounts=@($mounts)
        }
    }
}
function Runtime($Model, [object[]]$Containers) { Get-InVaultCompanyRuntimeStatus -CompanyId check-a -Model $Model -Containers $Containers }
$model=New-Model
$valid=Read-ConfigReport $model
Assert-True $valid.configurationValid 'A valid independent company configuration failed.'
Assert-True $valid.bootstrapEnabled 'The first-administrator setup flag was not reported.'
Assert-True ((Runtime $model @(New-Containers $model)).healthy) 'Healthy company containers were rejected.'
Assert-True (-not (Runtime $model @()).healthy) 'Missing deployment services were accepted.'
Assert-True (-not (Read-ConfigReport @{}).configurationValid) 'An empty configuration was accepted.'
$mutations=@(
    {param($m) $m.name='invault-other'},
    {param($m) $m.services.backend.environment.DB_NAME='another_database'},
    {param($m) $m.services.backend.environment.DB_PASSWORD='different-secret'},
    {param($m) $m.services.database.environment.MYSQL_PASSWORD='replace-with-a-long-random-password'},
    {param($m) $m.services.backend.environment.INVAULT_JWT_SECRET='short'},
    {param($m) $m.services.backend.environment.INVAULT_JWT_ISSUER='https://other.example.test'},
    {param($m) $m.services.backend.environment.INVAULT_CORS_ALLOWED_ORIGINS='http://a.example.test'},
    {param($m) $m.services.backend.environment.INVAULT_CORS_ALLOWED_ORIGINS='https://user:private@a.example.test'},
    {param($m) $m.services.database.ports=@(@{published='3306';target=3306})},
    {param($m) $m.services.backend.network_mode='host'},
    {param($m) $m.networks.data.internal=$false},
    {param($m) $m.networks.edge.name='other_edge'},
    {param($m) $m.volumes['mysql-data'].external=$true},
    {param($m) $m.volumes['mysql-data'].name='other_mysql-data'},
    {param($m) $m.services.database.volumes=@(@{type='bind';source='/tmp/test';target='/var/lib/mysql'})},
    {param($m) $m.services.gateway.ports=@(@{published='8080';target=80})},
    {param($m) $m.services.Remove('frontend')}
)
foreach ($mutation in $mutations) {
    $model=New-Model
    & $mutation $model
    Assert-True (-not (Read-ConfigReport $model).configurationValid) 'A modified company boundary passed configuration checks.'
}
$model=New-Model
$containers=@(New-Containers $model)
$containers[0].State.Running=$false
Assert-True (-not (Runtime $model $containers).healthy) 'A stopped database was accepted.'
$containers=@(New-Containers $model)
$containers[1].State.Health.Status='unhealthy'
Assert-True (-not (Runtime $model $containers).healthy) 'An unhealthy backend was accepted.'
$containers=@(New-Containers $model)
$containers[0].Config.Labels['com.docker.compose.project']='invault-other'
Assert-True (-not (Runtime $model $containers).healthy) 'A foreign company container was accepted.'
$containers=@(New-Containers $model)
$containers[0].Mounts[0].Name='other_mysql-data'
Assert-True (-not (Runtime $model $containers).healthy) 'A foreign persistent volume was accepted.'
$containers=@(New-Containers $model)
$containers[1].NetworkSettings.Networks['foreign_edge']=@{}
Assert-True (-not (Runtime $model $containers).healthy) 'An extra company network was accepted.'
$containers=@(New-Containers $model)
$containers[1].Config.Env=@($containers[1].Config.Env | Where-Object {$_ -notlike 'INVAULT_JWT_SECRET=*'}) + @('INVAULT_JWT_SECRET=different')
Assert-True (-not (Runtime $model $containers).healthy) 'A running backend with another signing secret was accepted.'
$containers=@(New-Containers $model)
$containers[0].HostConfig.PortBindings=@{'3306/tcp'=@(@{HostIp='0.0.0.0';HostPort='3306'})}
Assert-True (-not (Runtime $model $containers).healthy) 'A running database with published ports was accepted.'
$serialized=@((Read-ConfigReport $model),(Runtime $model @(New-Containers $model))) | ConvertTo-Json -Depth 12
Assert-True (-not $serialized.Contains($fixturePassword) -and -not $serialized.Contains($fixtureJwt)) 'Diagnostics exposed a database or JWT secret.'
$model.services.backend.environment.INVAULT_CORS_ALLOWED_ORIGINS='https://user:private@a.example.test'
Assert-True (-not ((Read-ConfigReport $model) | ConvertTo-Json -Depth 10).Contains('user:private')) 'An invalid URL exposed embedded credentials.'
Write-Host "Company diagnostics passed: $cases checks for configuration, runtime, isolation, stale settings and secret-free output."
