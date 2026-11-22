# Resume CLI Tool

A command-line tool for managing LaTeX resumes with Git branching for role-specific customization. Built with Kotlin and based on Jake Gutierrez's resume template.

## Features

- 🚀 **Quick Setup**: Initialize a résumé from Jake's LaTeX template
- 🌿 **Git Branching**: Create role-specific resume versions
- ✏️ **Interactive Editing**: Add/remove sections with guided prompts
- 📄 **LaTeX Generation**: Automatically generates clean LaTeX files
- 🔄 **Version Control**: Full git integration for tracking changes

## Prerequisites

- Java 17 or higher
- A LaTeX distribution with the packages the template uses (for `rsm compile`). The installers detect missing packages and offer to install them.
- Git is optional: rsm has Git built in (via JGit), but you'll want the `git` CLI to inspect or rebase your branches yourself.

## Installation

### Linux and macOS

```bash
curl -fsSL https://raw.githubusercontent.com/Badr-1/rsm/main/install.sh | bash
```

This installs the latest release into `~/.local/bin`, adds it to your `PATH`, and sets up shell completion for bash, zsh or fish. Restart your terminal afterwards.

To pass options, use `bash -s --`:

```bash
# a specific version
curl -fsSL https://raw.githubusercontent.com/Badr-1/rsm/main/install.sh | bash -s -- 2.0.2
# install missing LaTeX packages without asking
curl -fsSL https://raw.githubusercontent.com/Badr-1/rsm/main/install.sh | bash -s -- --yes
```

| Option | Description |
|---|---|
| `VERSION` | Version to install, e.g. `2.0.2`. Defaults to the latest release. |
| `--jar FILE` | Install from a local JAR instead of downloading it. |
| `-y`, `--yes` | Install missing LaTeX packages without asking (uses `sudo`). |
| `--skip-deps` | Never install system packages; only warn about missing ones. |
| `-h`, `--help` | Show help. |

If LaTeX packages are missing, the installer asks before running your package manager (apt, dnf, zypper, pacman or tlmgr; on macOS, Homebrew's BasicTeX plus tlmgr).

### Windows

In PowerShell:

```powershell
irm https://raw.githubusercontent.com/Badr-1/rsm/main/install.ps1 | iex
```

This installs into `%LOCALAPPDATA%\Programs\rsm` and adds it to your user `PATH`. If LaTeX is missing it offers to install MiKTeX via `winget`. To pass options, download the script and run it, e.g. `.\install.ps1 -Version 2.0.2`, `.\install.ps1 -Yes` or `.\install.ps1 -SkipDeps`. Shell completion isn't available on Windows.

### Uninstall

```bash
# Linux / macOS
curl -fsSL https://raw.githubusercontent.com/Badr-1/rsm/main/uninstall.sh | bash
```

```powershell
# Windows
.\install.ps1 -Uninstall
```

This removes rsm, its completions and the `PATH` entries the installer added. Java, LaTeX and your resume repositories are left alone.

### Build from source

The project depends on a fork of kotlin-inquirer included as a Git submodule, so clone with `--recursive`:

```bash
git clone --recursive https://github.com/Badr-1/rsm.git
cd rsm
# already cloned without --recursive? run:
# git submodule update --init

./gradlew jar
./install.sh --jar build/libs/rsm-<version>.jar   # Windows: .\install.ps1 -Jar build\libs\rsm-<version>.jar
```

`./gradlew createDistribution` builds an offline bundle (`build/distributions/rsm-<version>.zip`) with the JAR and the install scripts. Unzip it and run `./install.sh --jar rsm-<version>.jar`.

## Usage

### Initialize a New Resume

```bash
# navigate to your desired directory
rsm init
```

This will:
- Create a new git repository
- Download Jake's LaTeX template
- Prompt you for personal information
- Generate initial resume.tex file
- Make the first commit

### Create Role-Specific Versions

To create a new role-specific résumé, use the `role` command:
Note: the role name should be following git branch naming conventions (e.g., no spaces, special characters).
```bash
rsm role "software-engineer"
rsm role "frontend-developer"
rsm role "data-scientist"
```

to further customize your résumé for specific companies, you can use nested branches:
```bash
rsm role "google/software-engineer"
rsm role "meta/data-scientist"
rsm role "meta/data-scientist"
```

Each command creates a new git branch for role-specific customization.

### Add Content

```bash
rsm add
```

### Remove Content

```bash
rsm remove
```

### Update Content

```bash
rsm update
```

### Reorder Content

```bash
rsm reorder
```

### Generate LaTeX file

```bash
rsm generate
```

### Compile to Pdf
```bash
rsm compile
# to clear auxiliary files before compiling use 
rsm compile -c # or --clean
# to open the generated PDF after compiling
rsm compile -o # or --open
# to do both clean and open
rsm compile -co # or --clean --open
# to generate latex from configs before compiling
rsm compile -g # or --generate
# complete command to generate, compile and clear auxiliary files
rsm compile -gco # or --clean --generate --open
```


## Example Workflow

1.**Initialize your résumé**:
```bash
rsm init
```

2.**Create a role-specific version**:
```bash
rsm role "senior-backend-developer"
```

3.**Customize for the role**:
```bash
# Switch to the role branch and add relevant experience
rsm add
```

4.**Generate PDF**:
```bash
rsm compile
```

## Best Practice
 
My Opinion the best usage of this tool would be:
1. create a complete resume in your main branch don't leave out anything
2. when applying use `rsm role` to create a tailored resume for the position
3. change the resume based on the role, the changes should mostly be removal.

this flow helps with syncing the roles branches with main one when via rebasing without any conflicts

## Configuration

The tool stores configuration in `resume-config.json`:

```json
{
  "personalInfo": {
    "name": "John Doe",
    "phone": "+1-555-0123",
    "email": "john.doe@email.com",
    "linkedin": "https://linkedin.com/in/johndoe",
    "github": "https://github.com/johndoe"
  },
  "education": [...],
  "experience": [...],
  "projects": [...],
  "technicalSkills": {...}
}
```

## Git Workflow

The tool uses a branch-based workflow:

```
main
├── software-engineer
├── frontend-developer
├── data-scientist
└── devOps-engineer
```

- `main`: Your general resume template
- Feature branches: Role-specific customizations
- Each branch maintains its own version of the résumé

## LaTeX Template

Based on Jake Gutierrez's resume template:
- Clean, ATS-friendly design
- Proper sectioning and formatting
- Customizable sections
- PDF generation ready


### Sections

- `personalInfo`: Personal details
  - `name`: Full name
  - `phone`: Contact number
  - `email`: Email address
  - `linkedin`: LinkedIn profile URL
  - `github`: GitHub profile URL
- `education`: Academic background
- `experience`: Work experience
- `projects`: Personal/professional projects
- `skills`: Technical skills and technologies

  

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request


## License

MIT License—feel free to use and modify for your needs.

## Credits

- LaTeX template by [Jake Gutierrez](https://github.com/jakegut/resume)
- Built with Kotlin and Clikt for CLI functionality
- Built with kotlin-inquirer for interactive prompts
- Git integration via JGit library