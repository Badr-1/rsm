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
- Git
- LaTeX distribution (for compiling to PDF)

## Installation

1.Clone the repository:
```bash
git clone <repository-url>
cd rsm
```

2.Build the project:
```bash
./gradlew clean jar
```

3.Make the script executable:
```bash
chmod +x rsm
```

4.(Optional) Add to PATH:
```bash
# Add to ~/.bashrc or ~/.zshrc
export PATH=$PATH:/path/to/rsm
```

## Usage

### Initialize a New Resume

```bash
rsm init
```

This will:
- Create a new git repository
- Download Jake's LaTeX template
- Prompt you for personal information
- Generate initial resume.tex file
- Make the first commit

### Create Role-Specific Versions

To create a new role-specific résumé, use the `create` command:
Note: the role name should be following git branch naming conventions (e.g., no spaces, special characters).
```bash
rsm create "software-engineer"
rsm create "frontend-developer"
rsm create "data-scientist"
```

to further customize your résumé for specific companies, you can use nested branches:
```bash
rsm create "google/software-engineer"
rsm create "meta/data-scientist"
```

Each command creates a new git branch for role-specific customization.

### Add Content

```bash
# Add to current branch
rsm add experience
rsm add education
rsm add projects
rsm add skills

# Add to specific branch
rsm add experience --target "software-engineer"
rsm add projects --target main
```

### Remove Content

```bash
# Remove from current branch
rsm remove experience
rsm remove projects

# Remove from specific branch
rsm remove education --target "meta/data-scientist"
```

## Project Structure

```
resume-cli/
├── src/main/kotlin/
│   ├── Main.kt                 # CLI entry point
│   ├── ResumeManager.kt        # Core functionality
│   ├── LaTeXGenerator.kt       # LaTeX file generation
│   └── models/
│       └── ResumeData.kt       # Data classes
├── rsm                         # Shell script wrapper
├── build.gradle.kts            # Build configuration
└── README.md
```

## Example Workflow

1.**Initialize your résumé**:
```bash
rsm init
```

2.**Create a role-specific version**:
```bash
rsm create "senior-backend-developer"
```

3.**Customize for the role**:
```bash
# Switch to the role branch and add relevant experience
rsm add experience --target "Senior Backend Developer"
```

4.**Generate PDF**:
```bash
rsm compile
# to clear auxiliary files before compiling use 
rsm compile -c # or --clean
# to open the generated PDF after compiling
rsm compile -o # or --open
# to do both clean and open
rsm compile -co # or --clean --open
```

## Configuration

The tool stores configuration in `.resume-config.json`:

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

## Commands Reference

| Command            | Description                 | Example                        |
|--------------------|-----------------------------|--------------------------------|
| `init`             | Initialize new resume       | `rsm init`                     |
| `create <role>`    | Create role branch          | `rsm create "DevOps Engineer"` |
| `add <section>`    | Add content to section      | `rsm add experience`           |
| `remove <section>` | Remove content from section | `rsm remove projects`          |
| `compile`          | Compile LaTeX to PDF        | `rsm compile`                  |
| `status`           | Show repository status      | `rsm status`                   |

### Sections

- `education`: Academic background
- `experience`: Work experience
- `projects`: Personal/professional projects
- `skills`: Technical skills and technologies

### Options

- `--target <branch>`: Specify target branch (default: current branch)

## Development

### Building

```bash
./gradlew build
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## Troubleshooting

### Common Issues

**LaTeX compilation errors**:
```bash
# Install LaTeX distribution
sudo apt install texlive-full    # Ubuntu/Debian
brew install mactex              # macOS
```

**Permission denied**:
```bash
chmod +x rsm
```

## License

MIT License—feel free to use and modify for your needs.

## Credits

- LaTeX template by [Jake Gutierrez](https://github.com/jakegut/resume)
- Built with Kotlin and Clikt for CLI functionality
- Git integration via JGit library