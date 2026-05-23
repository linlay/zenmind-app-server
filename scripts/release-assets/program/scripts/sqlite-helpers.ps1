<#
.SYNOPSIS
  SQLite helpers using bundled System.Data.SQLite — replaces sqlite3 CLI dependency.
  Compatible with Windows PowerShell 5.1 (.NET Framework 4.7.2+).
#>

function Initialize-SqliteAssembly {
    <#
    .SYNOPSIS
      Load the bundled System.Data.SQLite assembly if not already loaded.
    #>
    if ([AppDomain]::CurrentDomain.GetAssemblies() | Where-Object { $_.GetName().Name -eq 'System.Data.SQLite' }) {
        return
    }
    $scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
    $dllPath = Join-Path $scriptDir 'lib\System.Data.SQLite.dll'
    if (-not (Test-Path -LiteralPath $dllPath)) {
        throw "System.Data.SQLite.dll not found at: $dllPath"
    }
    Add-Type -Path $dllPath
}

function New-SqliteConnection {
    <#
    .SYNOPSIS
      Create and open a new SQLite connection.
    .OUTPUTS
      System.Data.SQLite.SQLiteConnection — caller must dispose.
    #>
    param([string]$DbPath)
    Initialize-SqliteAssembly
    $connStr = "Data Source=$DbPath;Version=3;Journal Mode=WAL;Busy Timeout=5000"
    $conn = New-Object System.Data.SQLite.SQLiteConnection($connStr)
    $conn.Open()
    return $conn
}

function Invoke-SqliteNonQuery {
    <#
    .SYNOPSIS
      Execute one or more SQL statements (DDL / DML).
    .PARAMETER Sql
      SQL text.
    .PARAMETER Parameters
      Optional hashtable of parameter name→value pairs.
    #>
    param(
        [System.Data.SQLite.SQLiteConnection]$Connection,
        [string]$Sql,
        [hashtable]$Parameters = @{}
    )
    $cmd = $Connection.CreateCommand()
    try {
        $cmd.CommandText = $Sql
        foreach ($key in $Parameters.Keys) {
            $paramName = if ($key.StartsWith('@')) { $key } else { "@$key" }
            [void]$cmd.Parameters.AddWithValue($paramName, $Parameters[$key])
        }
        [void]$cmd.ExecuteNonQuery()
    } finally {
        $cmd.Dispose()
    }
}

function Invoke-SqliteScalar {
    <#
    .SYNOPSIS
      Execute a query and return the first column of the first row.
    #>
    param(
        [System.Data.SQLite.SQLiteConnection]$Connection,
        [string]$Sql,
        [hashtable]$Parameters = @{}
    )
    $cmd = $Connection.CreateCommand()
    try {
        $cmd.CommandText = $Sql
        foreach ($key in $Parameters.Keys) {
            $paramName = if ($key.StartsWith('@')) { $key } else { "@$key" }
            [void]$cmd.Parameters.AddWithValue($paramName, $Parameters[$key])
        }
        $result = $cmd.ExecuteScalar()
        if ($null -eq $result -or $result -is [System.DBNull]) {
            return $null
        }
        return $result
    } finally {
        $cmd.Dispose()
    }
}

function Invoke-SqliteQuery {
    <#
    .SYNOPSIS
      Execute a query and return rows as PSObjects.
    #>
    param(
        [System.Data.SQLite.SQLiteConnection]$Connection,
        [string]$Sql,
        [hashtable]$Parameters = @{}
    )
    $cmd = $Connection.CreateCommand()
    try {
        $cmd.CommandText = $Sql
        foreach ($key in $Parameters.Keys) {
            $paramName = if ($key.StartsWith('@')) { $key } else { "@$key" }
            [void]$cmd.Parameters.AddWithValue($paramName, $Parameters[$key])
        }
        $reader = $cmd.ExecuteReader()
        try {
            $results = @()
            while ($reader.Read()) {
                $row = [ordered]@{}
                for ($i = 0; $i -lt $reader.FieldCount; $i++) {
                    $val = $reader.GetValue($i)
                    if ($val -is [System.DBNull]) { $val = $null }
                    $row[$reader.GetName($i)] = $val
                }
                $results += [PSCustomObject]$row
            }
            return $results
        } finally {
            $reader.Dispose()
        }
    } finally {
        $cmd.Dispose()
    }
}
