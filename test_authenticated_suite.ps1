# ==============================================================================
# CampusConnect Phase 9: Authenticated Browser and API Test Suite
# Tests all 4 roles: STUDENT, ORGANIZER, JUDGE, ADMIN
# Target: http://localhost:8080/CampusConnect/
# ==============================================================================

param(
    [string]$BaseUrl = "http://localhost:8080/CampusConnect",
    [string]$ConfigFile = "C:\CBPCC-config\test_accounts.json",
    [int]$CdpPort = 9222,
    [int]$TimeoutSeconds = 10
)

$ErrorActionPreference = "Stop"

$results = [System.Collections.Generic.List[PSObject]]::new()

function Add-TestResult([string]$Category, [string]$TestName, [string]$Status, [string]$Detail) {
    $results.Add([PSCustomObject]@{
        Category = $Category
        Test     = $TestName
        Status   = $Status
        Detail   = $Detail
    })
}

# ------------------------------------------------------------------------------
# 1. Credential Resolution (In-Memory, Zero Printing)
# ------------------------------------------------------------------------------
$creds = @{
    STUDENT   = @{ Email = $env:CC_TEST_STUDENT_EMAIL;   Pass = $env:CC_TEST_STUDENT_PASS }
    ORGANIZER = @{ Email = $env:CC_TEST_ORGANIZER_EMAIL; Pass = $env:CC_TEST_ORGANIZER_PASS }
    JUDGE     = @{ Email = $env:CC_TEST_JUDGE_EMAIL;     Pass = $env:CC_TEST_JUDGE_PASS }
    ADMIN     = @{ Email = $env:CC_TEST_ADMIN_EMAIL;     Pass = $env:CC_TEST_ADMIN_PASS }
}

if (Test-Path $ConfigFile) {
    try {
        $json = Get-Content -Path $ConfigFile -Raw | ConvertFrom-Json
        if ($json.student -and -not $creds.STUDENT.Email) {
            $creds.STUDENT.Email = $json.student.email; $creds.STUDENT.Pass = $json.student.password
        }
        if ($json.organizer -and -not $creds.ORGANIZER.Email) {
            $creds.ORGANIZER.Email = $json.organizer.email; $creds.ORGANIZER.Pass = $json.organizer.password
        }
        if ($json.judge -and -not $creds.JUDGE.Email) {
            $creds.JUDGE.Email = $json.judge.email; $creds.JUDGE.Pass = $json.judge.password
        }
        if ($json.admin -and -not $creds.ADMIN.Email) {
            $creds.ADMIN.Email = $json.admin.email; $creds.ADMIN.Pass = $json.admin.password
        }
    } catch {
        Write-Warning "Could not parse config file $ConfigFile"
    }
}

function Has-Creds([string]$Role) {
    return (-not [string]::IsNullOrEmpty($creds[$Role].Email) -and -not [string]::IsNullOrEmpty($creds[$Role].Pass))
}

# ------------------------------------------------------------------------------
# 2. Direct API Authenticated Checks Helper
# ------------------------------------------------------------------------------
function Test-Role-Api([string]$Role) {
    if (-not (Has-Creds $Role)) {
        Add-TestResult "DIRECT_API" "$Role API Authentication & Flow" "BLOCKED" "Missing credentials for $Role"
        return $null
    }

    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $loginBody = @{ email = $creds[$Role].Email; password = $creds[$Role].Pass } | ConvertTo-Json

    try {
        # Login
        $loginResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json" -WebSession $session
        if (-not $loginResp.success -or $loginResp.user.role -ne $Role) {
            Add-TestResult "DIRECT_API" "$Role API Login" "FAIL" "Login succeeded but role was '$($loginResp.user.role)' (expected $Role)"
            return $null
        }
        $csrf = $loginResp.csrfToken
        Add-TestResult "DIRECT_API" "$Role API Login" "PASS" "Logged in successfully as $Role"

        # Verify /me
        $meResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/me" -Method Get -WebSession $session
        if ($meResp.success -and $meResp.user.role -eq $Role) {
            Add-TestResult "DIRECT_API" "$Role /api/auth/me Verification" "PASS" "Verified session principal role $Role"
        } else {
            Add-TestResult "DIRECT_API" "$Role /api/auth/me Verification" "FAIL" "Unexpected /me response"
        }

        # Role-Specific Direct API Operations
        switch ($Role) {
            "STUDENT" {
                # List teams
                $teams = Invoke-RestMethod -Uri "$BaseUrl/api/teams" -Method Get -WebSession $session
                Add-TestResult "DIRECT_API" "Student Teams Listing" "PASS" "Retrieved $($teams.teams.Count) teams"
                # List pending invitations
                $invites = Invoke-RestMethod -Uri "$BaseUrl/api/team-invitations" -Method Get -WebSession $session
                Add-TestResult "DIRECT_API" "Student Invitations Listing" "PASS" "Retrieved invitations list"
                # List registrations
                $regs = Invoke-RestMethod -Uri "$BaseUrl/api/registrations?page=1&pageSize=10" -Method Get -WebSession $session
                Add-TestResult "DIRECT_API" "Student Registrations Listing" "PASS" "Retrieved registrations (total: $($regs.total))"
            }
            "ORGANIZER" {
                # List managed events
                $mEvents = Invoke-RestMethod -Uri "$BaseUrl/api/manage/events?page=1&pageSize=10" -Method Get -WebSession $session
                Add-TestResult "DIRECT_API" "Organizer Managed Events Listing" "PASS" "Retrieved $($mEvents.events.Count) managed events"
                
                # Test draft creation and GET /api/manage/events/{id}
                $ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
                $draftCode = "AUTO-DRAFT-$ts"
                $openTime = [DateTime]::UtcNow.AddDays(1).ToString("yyyy-MM-ddTHH:mm:ssZ")
                $closeTime = [DateTime]::UtcNow.AddDays(2).ToString("yyyy-MM-ddTHH:mm:ssZ")
                $startTime = [DateTime]::UtcNow.AddDays(3).ToString("yyyy-MM-ddTHH:mm:ssZ")
                $endTime = [DateTime]::UtcNow.AddDays(4).ToString("yyyy-MM-ddTHH:mm:ssZ")

                $createBody = @{
                    eventCode = $draftCode
                    eventName = "Automated Draft Test $ts"
                    participationMode = "INDIVIDUAL"
                    registrationOpens = $openTime
                    registrationCloses = $closeTime
                    startsAt = $startTime
                    endsAt = $endTime
                    capacity = 10
                    location = "Automated Test Room"
                } | ConvertTo-Json

                $headers = @{ "X-CSRF-Token" = $csrf }
                $createResp = Invoke-RestMethod -Uri "$BaseUrl/api/manage/events" -Method Post -Body $createBody -Headers $headers -ContentType "application/json" -WebSession $session
                
                if ($createResp.success -and $createResp.event.eventId) {
                    $createdId = $createResp.event.eventId
                    Add-TestResult "DIRECT_API" "Organizer Create Draft Event" "PASS" "Created draft event ID $createdId"

                    # Verify GET /api/manage/events/{id} loads the draft event
                    $getDraft = Invoke-RestMethod -Uri "$BaseUrl/api/manage/events/$createdId" -Method Get -WebSession $session
                    if ($getDraft.success -and $getDraft.event.eventStatus -eq "DRAFT") {
                        Add-TestResult "DIRECT_API" "Organizer GET /api/manage/events/{id} (Draft)" "PASS" "Retrieved draft event #$createdId without 404"
                    } else {
                        Add-TestResult "DIRECT_API" "Organizer GET /api/manage/events/{id} (Draft)" "FAIL" "Failed to load draft event #$createdId"
                    }
                } else {
                    Add-TestResult "DIRECT_API" "Organizer Create Draft Event" "FAIL" "Could not create test draft"
                }
            }
            "JUDGE" {
                # List assigned events
                $jEvents = Invoke-RestMethod -Uri "$BaseUrl/api/judging/events" -Method Get -WebSession $session
                Add-TestResult "DIRECT_API" "Judge Assigned Events Listing" "PASS" "Retrieved assigned events"
            }
            "ADMIN" {
                # Dashboard metrics
                $dash = Invoke-RestMethod -Uri "$BaseUrl/api/admin/dashboard" -Method Get -WebSession $session
                if ($dash.success -and $dash.dashboard.users -ge 0) {
                    Add-TestResult "DIRECT_API" "Admin Dashboard Metrics" "PASS" "Loaded platform metrics (users: $($dash.dashboard.users), events: $($dash.dashboard.events))"
                } else {
                    Add-TestResult "DIRECT_API" "Admin Dashboard Metrics" "FAIL" "Dashboard failed"
                }
                # Users management
                $users = Invoke-RestMethod -Uri "$BaseUrl/api/admin/users?page=1&pageSize=10" -Method Get -WebSession $session
                Add-TestResult "DIRECT_API" "Admin User Management Listing" "PASS" "Retrieved $($users.users.Count) platform users"
                # Platform events
                $adminEvents = Invoke-RestMethod -Uri "$BaseUrl/api/admin/events?page=1&pageSize=10" -Method Get -WebSession $session
                Add-TestResult "DIRECT_API" "Admin Platform Events Listing" "PASS" "Retrieved $($adminEvents.events.Count) events"
                # Audit history
                $audit = Invoke-RestMethod -Uri "$BaseUrl/api/admin/audit?page=1&pageSize=10" -Method Get -WebSession $session
                Add-TestResult "DIRECT_API" "Admin Audit Trail Listing" "PASS" "Retrieved read-only audit log"
            }
        }

        # Logout with CSRF
        $logoutHeaders = @{ "X-CSRF-Token" = $csrf }
        $logoutResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/logout" -Method Post -Body "{}" -Headers $logoutHeaders -ContentType "application/json" -WebSession $session
        if ($logoutResp.success) {
            Add-TestResult "DIRECT_API" "$Role API Logout" "PASS" "Logged out successfully"
        } else {
            Add-TestResult "DIRECT_API" "$Role API Logout" "FAIL" "Logout failed"
        }

    } catch {
        Add-TestResult "DIRECT_API" "$Role API Flow" "FAIL" $_.Exception.Message
    }
}

# ------------------------------------------------------------------------------
# 3. Authenticated Browser Automation Helper (Edge CDP)
# ------------------------------------------------------------------------------
function Test-Role-Browser([string]$Role) {
    if (-not (Has-Creds $Role)) {
        Add-TestResult "BROWSER" "$Role Authenticated Browser Flow" "BLOCKED" "Missing credentials for $Role"
        return
    }

    $edgePath = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
    if (-not (Test-Path $edgePath)) {
        $edgePath = "C:\Program Files\Microsoft\Edge\Application\msedge.exe"
    }

    # Keep browser profiles isolated to this run; they hold authenticated cookies.
    $scratchDir = Join-Path $env:TEMP ("CampusConnect-Phase9-" + [Guid]::NewGuid().ToString("N"))
    $profileDir = Join-Path $scratchDir "edge_${Role}_profile"
    if (-not (Test-Path $profileDir)) {
        New-Item -ItemType Directory -Path $profileDir -Force | Out-Null
    }

    $edgeArgs = @(
        "--headless=new",
        "--remote-debugging-port=$CdpPort",
        "--user-data-dir=$profileDir",
        "--no-first-run",
        "--no-default-browser-check",
        "about:blank"
    )
    $proc = Start-Process -FilePath $edgePath -ArgumentList $edgeArgs -PassThru

    try {
        $startTime = [DateTime]::UtcNow
        $pageTarget = $null
        while (([DateTime]::UtcNow - $startTime).TotalSeconds -lt $TimeoutSeconds) {
            try {
                $targets = Invoke-RestMethod -Uri "http://127.0.0.1:$CdpPort/json/list" -UseBasicParsing -ErrorAction SilentlyContinue
                $pageTarget = $targets | Where-Object { $_.type -eq "page" } | Select-Object -First 1
                if ($null -ne $pageTarget -and -not [string]::IsNullOrEmpty($pageTarget.webSocketDebuggerUrl)) { break }
            } catch { }
            Start-Sleep -Milliseconds 250
        }

        if ($null -eq $pageTarget) { throw "CDP connection failed for $Role" }

        $ws = New-Object System.Net.WebSockets.ClientWebSocket
        $connectCts = New-Object System.Threading.CancellationTokenSource([TimeSpan]::FromSeconds($TimeoutSeconds))
        $ws.ConnectAsync((New-Object System.Uri($pageTarget.webSocketDebuggerUrl)), $connectCts.Token).Wait()

        $cmdId = 1
        function Send-CdpMsg([string]$Method, [hashtable]$Params = @{}) {
            $id = $global:cmdId++
            $payload = @{ id = $id; method = $Method; params = $Params } | ConvertTo-Json -Compress
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
            $sendCts = New-Object System.Threading.CancellationTokenSource([TimeSpan]::FromSeconds($TimeoutSeconds))
            $ws.SendAsync((New-Object System.ArraySegment[byte] -ArgumentList @(,$bytes)),
                [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $sendCts.Token).Wait()

            $recvBuf = New-Object byte[] 65536
            $accum = New-Object System.Collections.Generic.List[byte]
            $readCts = New-Object System.Threading.CancellationTokenSource([TimeSpan]::FromSeconds($TimeoutSeconds))

            while ($true) {
                $r = $ws.ReceiveAsync((New-Object System.ArraySegment[byte] -ArgumentList @(,$recvBuf)), $readCts.Token).Result
                for ($i = 0; $i -lt $r.Count; $i++) { $accum.Add($recvBuf[$i]) }
                if ($r.EndOfMessage) {
                    $text = [System.Text.Encoding]::UTF8.GetString($accum.ToArray())
                    $parsed = $text | ConvertFrom-Json
                    if ($parsed.id -eq $id) { return $parsed }
                    $accum.Clear()
                }
            }
        }

        function Eval-Cdp([string]$Expression) {
            $resp = Send-CdpMsg "Runtime.evaluate" @{ expression = $Expression; returnByValue = $true; awaitPromise = $true }
            if ($null -ne $resp.result -and $null -ne $resp.result.result) { return $resp.result.result.value }
            return $null
        }

        Send-CdpMsg "Runtime.enable" | Out-Null

        # Navigate to login
        Send-CdpMsg "Page.navigate" @{ url = "$BaseUrl/#/login" } | Out-Null
        Start-Sleep -Milliseconds 1500

        # Fill credentials & submit
        $email = $creds[$Role].Email
        $pass  = $creds[$Role].Pass

        # In-memory injection without logging password
        Eval-Cdp @"
            document.getElementById('login-email').value = '$email';
            document.getElementById('login-password').value = '$pass';
            const f = document.getElementById('login-form');
            f.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
"@ | Out-Null
        Start-Sleep -Milliseconds 2500

        $curHash = Eval-Cdp "window.location.hash"
        $welcomeName = Eval-Cdp "document.getElementById('user-display-name') ? document.getElementById('user-display-name').innerText : ''"
        $actualRole = Eval-Cdp "window.API && window.API.getUser() ? window.API.getUser().role : ''"

        if ($actualRole -ne $Role) {
            Add-TestResult "BROWSER" "$Role Login Role Verification" "FAIL" "Authenticated role did not match the requested role"
            return
        }

        if ($curHash -ne "#/login") {
            Add-TestResult "BROWSER" "$Role Login & Navigation" "PASS" "Logged in, redirected to '$curHash', User: '$welcomeName'"
        } else {
            $alert = Eval-Cdp "document.getElementById('login-alert') ? document.getElementById('login-alert').innerText : ''"
            Add-TestResult "BROWSER" "$Role Login & Navigation" "FAIL" "Remained on #/login. Alert: '$alert'"
            return
        }

        # Role-Specific Browser Flow Verification
        switch ($Role) {
            "STUDENT" {
                # Navigate to teams
                Send-CdpMsg "Page.navigate" @{ url = "$BaseUrl/#/student/teams" } | Out-Null
                Start-Sleep -Milliseconds 1500
                $teamsHeading = Eval-Cdp "document.querySelector('h1') ? document.querySelector('h1').innerText : ''"
                if ($teamsHeading -eq "Teams & Invitations") {
                    Add-TestResult "BROWSER" "Student Teams UI" "PASS" "Loaded student teams view"
                } else {
                    Add-TestResult "BROWSER" "Student Teams UI" "FAIL" "Expected student teams view; found '$teamsHeading'"
                }

                # Navigate to registrations
                Send-CdpMsg "Page.navigate" @{ url = "$BaseUrl/#/student/registrations" } | Out-Null
                Start-Sleep -Milliseconds 1500
                $regHeading = Eval-Cdp "document.querySelector('h1') ? document.querySelector('h1').innerText : ''"
                if ($regHeading -eq "My Registrations") {
                    Add-TestResult "BROWSER" "Student Registrations UI" "PASS" "Loaded student registrations view"
                } else {
                    Add-TestResult "BROWSER" "Student Registrations UI" "FAIL" "Expected student registrations view; found '$regHeading'"
                }
            }
            "ORGANIZER" {
                # Organizer Dashboard
                Send-CdpMsg "Page.navigate" @{ url = "$BaseUrl/#/organizer/dashboard" } | Out-Null
                Start-Sleep -Milliseconds 1500
                $orgHeading = Eval-Cdp "document.querySelector('h1') ? document.querySelector('h1').innerText : ''"
                if ($orgHeading -eq "Organizer Dashboard") {
                    Add-TestResult "BROWSER" "Organizer Dashboard UI" "PASS" "Loaded organizer dashboard"
                } else {
                    Add-TestResult "BROWSER" "Organizer Dashboard UI" "FAIL" "Expected organizer dashboard; found '$orgHeading'"
                }

                # Create event form
                Send-CdpMsg "Page.navigate" @{ url = "$BaseUrl/#/organizer/events/new" } | Out-Null
                Start-Sleep -Milliseconds 1500
                $formCodeInput = Eval-Cdp "document.getElementById('event-code') !== null"
                if ($formCodeInput) {
                    Add-TestResult "BROWSER" "Organizer Event Creation Form" "PASS" "Rendered new event form"
                } else {
                    Add-TestResult "BROWSER" "Organizer Event Creation Form" "FAIL" "Event code input was not rendered"
                }
            }
            "JUDGE" {
                # Judge Dashboard
                Send-CdpMsg "Page.navigate" @{ url = "$BaseUrl/#/judge/events" } | Out-Null
                Start-Sleep -Milliseconds 1500
                $judgeHeading = Eval-Cdp "document.querySelector('h1') ? document.querySelector('h1').innerText : ''"
                if ($judgeHeading -eq "Judging Dashboard") {
                    Add-TestResult "BROWSER" "Judge Dashboard UI" "PASS" "Loaded judge dashboard"
                } else {
                    Add-TestResult "BROWSER" "Judge Dashboard UI" "FAIL" "Expected judge dashboard; found '$judgeHeading'"
                }
            }
            "ADMIN" {
                # Admin Dashboard
                Send-CdpMsg "Page.navigate" @{ url = "$BaseUrl/#/admin/dashboard" } | Out-Null
                Start-Sleep -Milliseconds 1500
                $adminHeading = Eval-Cdp "document.querySelector('h1') ? document.querySelector('h1').innerText : ''"
                if ($adminHeading -eq "Administrator Dashboard") {
                    Add-TestResult "BROWSER" "Admin Dashboard UI" "PASS" "Loaded admin dashboard"
                } else {
                    Add-TestResult "BROWSER" "Admin Dashboard UI" "FAIL" "Expected admin dashboard; found '$adminHeading'"
                }

                # Check Admin Events Table for Private Draft label (Hide Public View for DRAFT)
                Send-CdpMsg "Page.navigate" @{ url = "$BaseUrl/#/admin/events" } | Out-Null
                Start-Sleep -Milliseconds 2000
                $draftVisibility = Eval-Cdp @"
                    (() => {
                      const rows = Array.from(document.querySelectorAll('tr'));
                      const drafts = rows.filter(row => row.children[4] && row.children[4].innerText.trim() === 'DRAFT');
                      const exposed = drafts.filter(row => Array.from(row.querySelectorAll('a')).some(a =>
                        a.innerText.trim() === 'Public View' || (a.getAttribute('href') || '').includes('#/events/')));
                      return JSON.stringify({ draftCount: drafts.length, exposedCount: exposed.length });
                    })()
"@ | ConvertFrom-Json
                if ($draftVisibility.draftCount -eq 0) {
                    Add-TestResult "BROWSER" "Admin DRAFT Events Hide Public View" "FAIL" "No DRAFT row rendered; visibility could not be verified"
                } elseif ($draftVisibility.exposedCount -eq 0) {
                    Add-TestResult "BROWSER" "Admin DRAFT Events Hide Public View" "PASS" "Verified $($draftVisibility.draftCount) DRAFT rows have no Public View link"
                } else {
                    Add-TestResult "BROWSER" "Admin DRAFT Events Hide Public View" "FAIL" "Found $($draftVisibility.exposedCount) DRAFT rows with Public View links"
                }
            }
        }
        # Logout via UI
        Eval-Cdp @"
            const logoutBtn = document.getElementById('btn-header-logout');
            if (logoutBtn) { logoutBtn.click(); }
"@ | Out-Null
        Start-Sleep -Milliseconds 1500
        $postLogoutHash = Eval-Cdp "window.location.hash"
        $loggedOut = Eval-Cdp "window.API.getUser() === null"
        if ($loggedOut -and $postLogoutHash -eq "#/events") {
            Add-TestResult "BROWSER" "$Role Logout via UI" "PASS" "Logged out through the UI"
        } else {
            Add-TestResult "BROWSER" "$Role Logout via UI" "FAIL" "UI logout did not reach the logged-out event view"
        }

        $closeCts = New-Object System.Threading.CancellationTokenSource([TimeSpan]::FromSeconds(5))
        $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "Done", $closeCts.Token).Wait()

    } finally {
        if ($null -ne $proc -and -not $proc.HasExited) {
            # Edge starts child processes that can keep the temporary profile
            # locked after the top-level process exits. Stop the whole tree.
            try { & taskkill.exe /PID $proc.Id /T /F 2>&1 | Out-Null } catch { }
        }
        if (Test-Path -LiteralPath $profileDir) {
            Start-Sleep -Milliseconds 300
            try {
                Remove-Item -LiteralPath $profileDir -Recurse -Force -ErrorAction Stop
            } catch {
                # Report cleanup separately while allowing collected test
                # results to be emitted. The profile contains session cookies.
                Add-TestResult "BROWSER" "$Role Temporary Profile Cleanup" "FAIL" "Could not remove the isolated browser profile"
            }
        }
    }
}

# ------------------------------------------------------------------------------
# 4. Execute Suite Across All Roles
# ------------------------------------------------------------------------------
$roles = @("STUDENT", "ORGANIZER", "JUDGE", "ADMIN")

foreach ($r in $roles) {
    Test-Role-Api $r
    try {
        Test-Role-Browser $r
    } catch {
        Add-TestResult "BROWSER" "$r Browser Flow" "FAIL" "Browser test could not complete; see local PowerShell diagnostics"
    }
}

# Output Results Table
$results | Format-Table -AutoSize
