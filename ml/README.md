<h1 align="center">legis-entropy</h1>
<p align="center">
  <img alt="Rust" src="https://img.shields.io/badge/Rust-000000?logo=rust&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue">
  <img alt="Status" src="https://img.shields.io/badge/status-beta-brightgreen">
</p>

<br>

**legis-entropy** is an AI-powered legal analysis system designed to detect contradictions, duplications, and outdated norms in legislative documents. Built for the AI inDrive track by team **jdev**.

## features

- parsing and analysis of regulatory legal documents (NPA)
- detection of contradictions, duplications, and outdated norms
- visualization of relationships between laws
- explainability: AI-powered reasoning for why norms are flagged as problematic
- support for Kazakhstan legal databases (Әділет, data.egov.kz)
- export and analysis of open government data

## installation

### prerequisites:

- rust 1.70+
- cargo

### from source:

```bash
git clone https://github.com/jvmdevelop/legis-entropy.git
cd legis-entropy/
cargo build # the binary will appear in target/debug/
