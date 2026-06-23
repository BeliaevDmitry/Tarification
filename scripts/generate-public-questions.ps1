param(
    [Parameter(Mandatory = $true)][string]$FirstStageDocx,
    [Parameter(Mandatory = $true)][string]$AttestationDocx,
    [Parameter(Mandatory = $true)][string]$VerifiedAnswersDocx,
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

$firstStageXml = Read-DocumentXml $FirstStageDocx
$firstStageNs = New-WordNamespaceManager $firstStageXml
$firstStageQuestions = [System.Collections.Generic.List[object]]::new()
foreach ($table in $firstStageXml.SelectNodes('//w:tbl', $firstStageNs)) {
    $paragraphs = [System.Collections.Generic.List[string]]::new()
    foreach ($paragraph in $table.SelectNodes('.//w:p', $firstStageNs)) {
        $value = (($paragraph.SelectNodes('.//w:t', $firstStageNs) | ForEach-Object { $_.InnerText }) -join '').Trim()
        if ($value) { $paragraphs.Add($value) }
    }
    if ($paragraphs.Count -lt 3) { continue }
    $number = ($paragraphs[0] -replace '^\s*([0-9]+\.).*$', '$1').Trim()
    $question = ($paragraphs[0] -replace '^\s*[0-9]+\.\s+[^:]+:\s*', '').Trim()
    $page = ($paragraphs[1] -replace '^\D+', '').Trim()
    $answerParts = [System.Collections.Generic.List[string]]::new()
    $inlineAnswer = ($paragraphs[2] -replace '^[^:]+:\s*', '').Trim()
    if ($inlineAnswer) { $answerParts.Add($inlineAnswer) }
    if ($paragraphs.Count -gt 3) {
        $answerParts.AddRange($paragraphs.GetRange(3, $paragraphs.Count - 3))
    }
    $answer = ($answerParts -join "`n").Trim()
    $firstStageQuestions.Add([ordered]@{ number = $number; question = $question; answer = $answer; page = $page })
}

$verifiedXml = Read-DocumentXml $VerifiedAnswersDocx
$verifiedNs = New-WordNamespaceManager $verifiedXml
$verifiedAnswers = [System.Collections.Generic.List[object]]::new()
$verifiedTitle = $null
$verifiedNumber = $null
$verifiedLines = [System.Collections.Generic.List[string]]::new()
foreach ($paragraph in $verifiedXml.SelectNodes('//w:body/w:p', $verifiedNs)) {
    $value = (($paragraph.SelectNodes('.//w:t', $verifiedNs) | ForEach-Object { $_.InnerText }) -join '').Trim()
    if (-not $value) { continue }
    if ($value -match '^([1-9])\.\s+(.+)$') {
        if ($verifiedTitle) {
            $verifiedAnswers.Add([ordered]@{ number = "$verifiedNumber."; question = $verifiedTitle; details = ($verifiedLines -join "`n") })
        }
        $verifiedNumber = $matches[1]
        $verifiedTitle = $matches[2]
        $verifiedLines = [System.Collections.Generic.List[string]]::new()
    } elseif ($verifiedTitle) {
        $bullet = [char]0x2022
        if ($verifiedNumber -eq '9' -and $verifiedLines.Count -gt 0 -and
                $verifiedLines[$verifiedLines.Count - 1].StartsWith($bullet) -and -not $value.StartsWith($bullet)) {
            break
        }
        $verifiedLines.Add($value)
    }
}
if ($verifiedTitle) {
    $verifiedAnswers.Add([ordered]@{ number = "$verifiedNumber."; question = $verifiedTitle; details = ($verifiedLines -join "`n") })
}

$payload = [ordered]@{
    questions = $questions
    firstStageQuestions = $firstStageQuestions
    verifiedAnswers = $verifiedAnswers
} | ConvertTo-Json -Depth 5 -Compress
$javascript = "window.PUBLIC_QUESTIONS_DATA = $payload;`n"
[System.IO.File]::WriteAllText($OutputFile, $javascript, [System.Text.UTF8Encoding]::new($false))
Write-Host "Generated $($questions.Count) attestation questions, $($firstStageQuestions.Count) first-stage questions and $($verifiedAnswers.Count) verified answers."
