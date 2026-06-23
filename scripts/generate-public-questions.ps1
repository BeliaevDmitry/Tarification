param(
    [Parameter(Mandatory = $true)][string]$QuestionsDocx,
    [Parameter(Mandatory = $true)][string]$AttestationDocx,
    [Parameter(Mandatory = $true)][string]$OutputFile
)

Add-Type -AssemblyName System.IO.Compression.FileSystem
$wordNamespace = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'

function Read-DocumentXml([string]$path) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($path)
    try {
        $entry = $archive.GetEntry('word/document.xml')
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try {
            $document = [xml]$reader.ReadToEnd()
            Write-Output -NoEnumerate $document
        } finally { $reader.Dispose() }
    } finally {
        $archive.Dispose()
    }
}

function New-WordNamespaceManager($xml) {
    $manager = [System.Xml.XmlNamespaceManager]::new($xml.NameTable)
    $manager.AddNamespace('w', $wordNamespace)
    Write-Output -NoEnumerate $manager
}

function Get-NodeParagraphText($node, $namespaceManager) {
    $parts = foreach ($paragraph in $node.SelectNodes('.//w:p', $namespaceManager)) {
        $value = (($paragraph.SelectNodes('.//w:t', $namespaceManager) | ForEach-Object { $_.InnerText }) -join '').Trim()
        if ($value) { $value }
    }
    return ($parts -join "`n").Trim()
}

$attestationXml = Read-DocumentXml $AttestationDocx
$attestationNs = New-WordNamespaceManager $attestationXml
$questions = [System.Collections.Generic.List[object]]::new()
foreach ($row in $attestationXml.SelectNodes('//w:tbl/w:tr', $attestationNs)) {
    $cells = $row.SelectNodes('./w:tc', $attestationNs)
    if ($cells.Count -lt 3) { continue }
    $number = Get-NodeParagraphText $cells[0] $attestationNs
    $question = Get-NodeParagraphText $cells[1] $attestationNs
    $answer = Get-NodeParagraphText $cells[2] $attestationNs
    if (-not $question) { continue }
    if ($questions.Count -eq 0 -and $number -notmatch '\d') { continue }
    $questions.Add([ordered]@{ number = $number; question = $question; answer = $answer })
}

$recognizedXml = Read-DocumentXml $QuestionsDocx
$recognizedNs = New-WordNamespaceManager $recognizedXml
$pages = [System.Collections.Generic.List[object]]::new()
$currentTitle = $null
$currentLines = [System.Collections.Generic.List[string]]::new()
foreach ($paragraph in $recognizedXml.SelectNodes('//w:body/w:p', $recognizedNs)) {
    $value = (($paragraph.SelectNodes('.//w:t', $recognizedNs) | ForEach-Object { $_.InnerText }) -join '').Trim()
    if (-not $value) { continue }
    if ($value.Length -lt 20 -and $value -match '\s+\d+$') {
        if ($currentTitle -and $currentLines.Count) {
            $pages.Add([ordered]@{ title = $currentTitle; text = ($currentLines -join "`n") })
        }
        $currentTitle = $value
        $currentLines = [System.Collections.Generic.List[string]]::new()
    } elseif ($currentTitle) {
        $currentLines.Add($value)
    }
}
if ($currentTitle -and $currentLines.Count) {
    $pages.Add([ordered]@{ title = $currentTitle; text = ($currentLines -join "`n") })
}

$payload = [ordered]@{ questions = $questions; recognizedPages = $pages } | ConvertTo-Json -Depth 5 -Compress
$javascript = "window.PUBLIC_QUESTIONS_DATA = $payload;`n"
[System.IO.File]::WriteAllText($OutputFile, $javascript, [System.Text.UTF8Encoding]::new($false))
Write-Host "Generated $($questions.Count) questions and $($pages.Count) recognized pages."
