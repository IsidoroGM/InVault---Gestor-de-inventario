#requires -Version 7.3
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-CompanyValue($Object, [string[]]$Path) {
    $current = $Object
    foreach ($part in $Path) {
        if ($current -isnot [Collections.IDictionary] -or -not $current.Contains($part)) { return $null }
        $current = $current[$part]
    }
    return $current
}
function Assert-CompanyId([string]$CompanyId) {
    if ($CompanyId -cnotmatch '^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$' -or $CompanyId.Length -lt 2 -or $CompanyId.Length -gt 24 -or $CompanyId -eq 'preview') { throw 'Invalid company ID.' }
}
function Test-ConfiguredSecret($Value) {
    return $Value -is [string] -and $Value.Length -ge 32 -and $Value -notmatch '(?i)^replace|^change.?me|^example'
}

function Get-InVaultCompanyConfigurationStatus {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$CompanyId, [Parameter(Mandatory)][Collections.IDictionary]$Model)
    Assert-CompanyId $CompanyId
    $project = "invault-$CompanyId"
    $database = 'invault_' + $CompanyId.Replace('-', '_')
    $checks = [Collections.Generic.List[object]]::new()
    $checks.Add(@{code='project_identity'; ok=((Read-CompanyValue $Model @('name')) -ceq $project)})
    $db = Read-CompanyValue $Model @('services','database','environment')
    $backend = Read-CompanyValue $Model @('services','backend','environment')
    $checks.Add(@{code='database_identity'; ok=((Read-CompanyValue $db @('MYSQL_DATABASE')) -ceq $database -and (Read-CompanyValue $backend @('DB_NAME')) -ceq $database)})
    $checks.Add(@{code='private_database_host'; ok=((Read-CompanyValue $backend @('DB_HOST')) -ceq 'database')})
    $dbUser = Read-CompanyValue $db @('MYSQL_USER')
    $checks.Add(@{code='database_user'; ok=($dbUser -is [string] -and $dbUser -cmatch '^[a-zA-Z][a-zA-Z0-9_]{0,31}$' -and $dbUser -ceq (Read-CompanyValue $backend @('DB_USERNAME')))})
    $password = Read-CompanyValue $db @('MYSQL_PASSWORD')
    $rootPassword = Read-CompanyValue $db @('MYSQL_ROOT_PASSWORD')
    $checks.Add(@{code='database_credentials'; ok=((Test-ConfiguredSecret $password) -and (Test-ConfiguredSecret $rootPassword) -and $password -cne $rootPassword -and $password -ceq (Read-CompanyValue $backend @('DB_PASSWORD')))})
    $jwt = Read-CompanyValue $backend @('INVAULT_JWT_SECRET')
    $validJwt = $false
    try { $validJwt = $jwt -is [string] -and [Convert]::FromBase64String($jwt).Length -ge 32 } catch { }
    $checks.Add(@{code='jwt_secret'; ok=$validJwt})
    $origin = Read-CompanyValue $backend @('INVAULT_CORS_ALLOWED_ORIGINS')
    $uri = $null
    $validOrigin = $origin -is [string] -and [Uri]::TryCreate($origin, [UriKind]::Absolute, [ref]$uri) -and
        $uri.Scheme -ceq 'https' -and $uri.Port -eq 443 -and $uri.UserInfo -eq '' -and $uri.Query -eq '' -and
        $uri.Fragment -eq '' -and $uri.AbsolutePath -eq '/' -and $origin -ceq $uri.GetLeftPart([UriPartial]::Authority) -and
        $uri.Host -ceq (Read-CompanyValue $Model @('services','gateway','environment','APP_DOMAIN'))
    $checks.Add(@{code='public_https_origin'; ok=$validOrigin})
    $checks.Add(@{code='session_origins'; ok=($validOrigin -and $origin -ceq (Read-CompanyValue $backend @('INVAULT_JWT_ISSUER')) -and $origin -ceq (Read-CompanyValue $backend @('INVAULT_REALTIME_ALLOWED_ORIGINS')))})
    $checks.Add(@{code='production_profile'; ok=((Read-CompanyValue $backend @('SPRING_PROFILES_ACTIVE')) -ceq 'prod')})
    foreach ($service in @('database','backend','frontend','gateway')) {
        $definition = Read-CompanyValue $Model @('services',$service)
        $checks.Add(@{code="$service`_defined"; ok=($definition -is [Collections.IDictionary])})
        $ports = Read-CompanyValue $definition @('ports')
        if ($service -ne 'gateway') { $checks.Add(@{code="$service`_private"; ok=(@($ports).Where({$null -ne $_}).Count -eq 0)}) }
        $checks.Add(@{code="$service`_network_mode"; ok=([string]::IsNullOrEmpty((Read-CompanyValue $definition @('network_mode'))))})
    }
    $checks.Add(@{code='internal_data_network'; ok=((Read-CompanyValue $Model @('networks','data','internal')) -eq $true)})
    $checks.Add(@{code='company_network_names'; ok=((Read-CompanyValue $Model @('networks','data','name')) -ceq "$project`_data" -and (Read-CompanyValue $Model @('networks','edge','name')) -ceq "$project`_edge")})
    $checks.Add(@{code='company_database_volume'; ok=((Read-CompanyValue $Model @('volumes','mysql-data','name')) -ceq "$project`_mysql-data" -and (Read-CompanyValue $Model @('volumes','mysql-data','external')) -ne $true)})
    $mounts = @(Read-CompanyValue $Model @('services','database','volumes')) | Where-Object { (Read-CompanyValue $_ @('target')) -ceq '/var/lib/mysql' }
    $checks.Add(@{code='database_volume_mount'; ok=(@($mounts).Count -eq 1 -and (Read-CompanyValue (@($mounts)[0]) @('type')) -ceq 'volume' -and (Read-CompanyValue (@($mounts)[0]) @('source')) -ceq 'mysql-data')})
    $gatewayPorts = @(Read-CompanyValue $Model @('services','gateway','ports'))
    $http = @($gatewayPorts | Where-Object { (Read-CompanyValue $_ @('target')) -eq 80 -and (Read-CompanyValue $_ @('published')) -eq 80 -and (Read-CompanyValue $_ @('protocol')) -in @($null,'tcp') })
    $https = @($gatewayPorts | Where-Object { (Read-CompanyValue $_ @('target')) -eq 443 -and (Read-CompanyValue $_ @('published')) -eq 443 -and (Read-CompanyValue $_ @('protocol')) -in @($null,'tcp') })
    $checks.Add(@{code='gateway_http_https_ports'; ok=($http.Count -eq 1 -and $https.Count -eq 1)})
    [PSCustomObject]@{
        companyId=$CompanyId; project=$project; configurationValid=(@($checks | Where-Object {-not $_.ok}).Count -eq 0)
        publicUrl=$(if ($validOrigin) { $origin } else { $null })
        bootstrapEnabled=((Read-CompanyValue $backend @('INVAULT_BOOTSTRAP_ADMIN_ENABLED')) -eq 'true')
        checks=$checks
    }
}

function Get-InVaultCompanyRuntimeStatus {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$CompanyId, [Parameter(Mandatory)][Collections.IDictionary]$Model,
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Containers)
    Assert-CompanyId $CompanyId
    $project = "invault-$CompanyId"
    $checks = [Collections.Generic.List[object]]::new()
    $services = [Collections.Generic.List[object]]::new()
    foreach ($service in @('database','backend','frontend','gateway')) {
        $matching = @($Containers | Where-Object { (Read-CompanyValue $_ @('Config','Labels','com.docker.compose.service')) -ceq $service })
        $checks.Add(@{code="$service`_single_container"; ok=($matching.Count -eq 1)})
        if ($matching.Count -ne 1) { continue }
        $container = $matching[0]
        $checks.Add(@{code="$service`_company_label"; ok=((Read-CompanyValue $container @('Config','Labels','com.docker.compose.project')) -ceq $project)})
        $running = (Read-CompanyValue $container @('State','Running')) -eq $true
        $health = Read-CompanyValue $container @('State','Health','Status')
        $healthy = $running -and ($health -ceq 'healthy' -or ($service -eq 'gateway' -and [string]::IsNullOrEmpty($health)))
        $checks.Add(@{code="$service`_healthy"; ok=$healthy})
        if ($service -ne 'gateway') {
            $bindings = Read-CompanyValue $container @('HostConfig','PortBindings')
            $published = $false
            if ($bindings -is [Collections.IDictionary]) {
                foreach ($value in $bindings.Values) { if (@($value).Where({$null -ne $_}).Count -gt 0) { $published = $true } }
            }
            $checks.Add(@{code="$service`_no_published_ports"; ok=(-not $published)})
        }
        $actualNetworks = Read-CompanyValue $container @('NetworkSettings','Networks')
        $expectedNetworks = if ($service -eq 'database') { @("$project`_data") } elseif ($service -eq 'backend') { @("$project`_data", "$project`_edge") } else { @("$project`_edge") }
        $networkNames = if ($actualNetworks -is [Collections.IDictionary]) { @($actualNetworks.Keys | Sort-Object) } else { @() }
        $checks.Add(@{code="$service`_company_networks"; ok=(($networkNames -join ',') -ceq (($expectedNetworks | Sort-Object) -join ','))})
        if ($service -eq 'database') {
            $mounts = @(Read-CompanyValue $container @('Mounts')) | Where-Object { (Read-CompanyValue $_ @('Destination')) -ceq '/var/lib/mysql' }
            $checks.Add(@{code='database_persistent_volume'; ok=(@($mounts).Count -eq 1 -and (Read-CompanyValue (@($mounts)[0]) @('Type')) -ceq 'volume' -and (Read-CompanyValue (@($mounts)[0]) @('Name')) -ceq "$project`_mysql-data")})
        }
        if ($service -in @('database','backend')) {
            $actualEnvironment = @{}
            foreach ($entry in @(Read-CompanyValue $container @('Config','Env'))) {
                if ($entry -is [string] -and $entry.Contains('=')) { $split = $entry.IndexOf('='); $actualEnvironment[$entry.Substring(0,$split)] = $entry.Substring($split+1) }
            }
            $expectedEnvironment = Read-CompanyValue $Model @('services',$service,'environment')
            $keys = if ($service -eq 'database') { @('MYSQL_DATABASE','MYSQL_USER','MYSQL_PASSWORD','MYSQL_ROOT_PASSWORD') } else { @('DB_HOST','DB_NAME','DB_USERNAME','DB_PASSWORD','SPRING_PROFILES_ACTIVE','INVAULT_BOOTSTRAP_ADMIN_ENABLED','INVAULT_JWT_SECRET','INVAULT_JWT_ISSUER','INVAULT_CORS_ALLOWED_ORIGINS','INVAULT_REALTIME_ALLOWED_ORIGINS') }
            $matches = $true
            foreach ($key in $keys) {
                if ((Read-CompanyValue $actualEnvironment @($key)) -cne (Read-CompanyValue $expectedEnvironment @($key))) { $matches = $false }
            }
            $checks.Add(@{code="$service`_configuration_matches"; ok=$matches})
        }
        $imageId = Read-CompanyValue $container @('Image')
        $services.Add(@{service=$service; running=$running; healthy=$healthy; imageId=$(if ($imageId -cmatch '^sha256:[a-f0-9]{64}$') {$imageId} else {$null})})
    }
    [PSCustomObject]@{healthy=(@($checks | Where-Object {-not $_.ok}).Count -eq 0); services=$services; checks=$checks}
}

Export-ModuleMember -Function Get-InVaultCompanyConfigurationStatus, Get-InVaultCompanyRuntimeStatus
