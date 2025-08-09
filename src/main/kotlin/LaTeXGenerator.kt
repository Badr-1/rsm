import models.Education
import models.Experience
import models.PersonalInfo
import models.Project
import models.ResumeData
import models.TechnicalSkills

// LaTeX file generator
// File: src/main/kotlin/LaTeXGenerator.kt
class LaTeXGenerator {

    fun generate(data: ResumeData): String {
        return buildString {
            appendLine(generateHeader())
            appendLine(generatePersonalInfo(data.personalInfo))
            appendLine(generateEducation(data.education))
            appendLine(generateExperience(data.experience))
            appendLine(generateProjects(data.projects))
            appendLine(generateTechnicalSkills(data.technicalSkills))
            appendLine(generateFooter())
        }
    }

    private fun generateHeader(): String {
        return """
%-------------------------
% Resume in Latex
% Author : Jake Gutierrez
% Based off of: https://github.com/sb2nov/resume
% License : MIT
%------------------------

\documentclass[letterpaper,11pt]{article}

\usepackage{latexsym}
\usepackage[empty]{fullpage}
\usepackage{titlesec}
\usepackage{marvosym}
\usepackage[usenames,dvipsnames]{color}
\usepackage{verbatim}
\usepackage{enumitem}
\usepackage[hidelinks]{hyperref}
\usepackage{fancyhdr}
\usepackage[english]{babel}
\usepackage{tabularx}
\input{glyphtounicode}


%----------FONT OPTIONS----------
% sans-serif
% \usepackage[sfdefault]{FiraSans}
% \usepackage[sfdefault]{roboto}
% \usepackage[sfdefault]{noto-sans}
% \usepackage[default]{sourcesanspro}

% serif
% \usepackage{CormorantGaramond}
% \usepackage{charter}


\pagestyle{fancy}
\fancyhf{} % clear all header and footer fields
\fancyfoot{}
\renewcommand{\headrulewidth}{0pt}
\renewcommand{\footrulewidth}{0pt}

% Adjust margins
\addtolength{\oddsidemargin}{-0.5in}
\addtolength{\evensidemargin}{-0.5in}
\addtolength{\textwidth}{1in}
\addtolength{\topmargin}{-.5in}
\addtolength{\textheight}{1.0in}

\urlstyle{same}

\raggedbottom
\raggedright
\setlength{\tabcolsep}{0in}

% Sections formatting
\titleformat{\section}{
  \vspace{-4pt}\scshape\raggedright\large
}{}{0em}{}[\color{black}\titlerule \vspace{-5pt}]

% Ensure that generate pdf is machine readable/ATS parsable
\pdfgentounicode=1

%-------------------------
% Custom commands
\newcommand{\resumeItem}[1]{
  \item\small{
    {#1 \vspace{-2pt}}
  }
}

\newcommand{\resumeSubheading}[4]{
  \vspace{-2pt}\item
    \begin{tabular*}{0.97\textwidth}[t]{l@{\extracolsep{\fill}}r}
      \textbf{#1} & #2 \\
      \textit{\small#3} & \textit{\small #4} \\
    \end{tabular*}\vspace{-7pt}
}

\newcommand{\resumeSubSubheading}[2]{
    \item
    \begin{tabular*}{0.97\textwidth}{l@{\extracolsep{\fill}}r}
      \textit{\small#1} & \textit{\small #2} \\
    \end{tabular*}\vspace{-7pt}
}

\newcommand{\resumeProjectHeading}[2]{
    \item
    \begin{tabular*}{0.97\textwidth}{l@{\extracolsep{\fill}}r}
      \small#1 & #2 \\
    \end{tabular*}\vspace{-7pt}
}

\newcommand{\resumeSubItem}[1]{\resumeItem{#1}\vspace{-4pt}}

\renewcommand\labelitemii{$\vcenter{\hbox{\tiny$\bullet$}}$}

\newcommand{\resumeSubHeadingListStart}{\begin{itemize}[leftmargin=0.15in, label={}]}
\newcommand{\resumeSubHeadingListEnd}{\end{itemize}}
\newcommand{\resumeItemListStart}{\begin{itemize}}
\newcommand{\resumeItemListEnd}{\end{itemize}\vspace{-5pt}}

%-------------------------------------------
%%%%%%  RESUME STARTS HERE  %%%%%%%%%%%%%%%%%%%%%%%%%%%%


\begin{document}

%----------HEADING----------
        """.trimIndent()
    }

    private fun generatePersonalInfo(info: PersonalInfo): String {
        return """
% \begin{tabular*}{\textwidth}{l@{\extracolsep{\fill}}r}
%   \textbf{\href{http://sourabhbajaj.com/}{\Large Sourabh Bajaj}} & Email : \href{mailto:sourabh@sourabhbajaj.com}{sourabh@sourabhbajaj.com}\\
%   \href{http://sourabhbajaj.com/}{http://www.sourabhbajaj.com} & Mobile : +1-123-456-7890 \\
% \end{tabular*}

\begin{center}
    \textbf{\Huge \scshape ${info.name}} \\ \vspace{1pt}
    \small ${info.phone} $|$ \href{mailto:${info.email}}{\underline{${info.email}}} $|$ 
    \href{${info.linkedin}}{\underline{${extractUsernameFromUrl(info.linkedin)}}} $|$
    \href{${info.github}}{\underline{${extractUsernameFromUrl(info.github)}}}
\end{center}
        """.trimIndent()
    }

    private fun generateEducation(education: List<Education>): String {
        if (education.isEmpty()) return ""

        return buildString {
            appendLine("%-----------EDUCATION-----------")
            appendLine("\\section{Education}")
            appendLine("  \\resumeSubHeadingListStart")

            education.forEach { edu ->
                appendLine("    \\resumeSubheading")
                appendLine("      {${edu.institution}}{${edu.location}}")
                appendLine("      {${edu.degree}${if (edu.gpa.isNotEmpty()) "; GPA: ${edu.gpa}" else ""}}{${edu.graduationDate}}")

                if (edu.relevantCourses.isNotEmpty()) {
                    appendLine("      \\resumeItemListStart")
                    appendLine("        \\resumeItem{\\textbf{Relevant Courses:} ${edu.relevantCourses.joinToString(", ")}}")
                    appendLine("      \\resumeItemListEnd")
                }
            }

            appendLine("  \\resumeSubHeadingListEnd")
        }
    }

    private fun generateExperience(experience: List<Experience>): String {
        if (experience.isEmpty()) return ""

        return buildString {
            appendLine("%-----------EXPERIENCE-----------")
            appendLine("\\section{Experience}")
            appendLine("  \\resumeSubHeadingListStart")

            experience.forEach { exp ->
                appendLine("    \\resumeSubheading")
                appendLine("      {${exp.position}}{${exp.startDate} -- ${exp.endDate}}")
                appendLine("      {${exp.company}}{${exp.location}}")

                if (exp.bullets.isNotEmpty()) {
                    appendLine("      \\resumeItemListStart")
                    exp.bullets.forEach { bullet ->
                        appendLine("        \\resumeItem{${bullet}}")
                    }
                    appendLine("      \\resumeItemListEnd")
                }
            }

            appendLine("  \\resumeSubHeadingListEnd")
        }
    }

    private fun generateProjects(projects: List<Project>): String {
        if (projects.isEmpty()) return ""

        return buildString {
            appendLine("%-----------PROJECTS-----------")
            appendLine("\\section{Projects}")
            appendLine("    \\resumeSubHeadingListStart")

            projects.forEach { project ->
                appendLine("      \\resumeProjectHeading")
                appendLine("          {\\textbf{${project.name}} $|$ \\emph{${project.technologies}}}{${project.startDate} -- ${project.endDate}}")

                if (project.bullets.isNotEmpty()) {
                    appendLine("          \\resumeItemListStart")
                    project.bullets.forEach { bullet ->
                        appendLine("            \\resumeItem{${bullet}}")
                    }
                    appendLine("          \\resumeItemListEnd")
                }
            }

            appendLine("    \\resumeSubHeadingListEnd")
        }
    }

    private fun generateTechnicalSkills(skills: TechnicalSkills): String {
        return buildString {
            appendLine("%-----------PROGRAMMING SKILLS-----------")
            appendLine("\\section{Technical Skills}")
            appendLine(" \\begin{itemize}[leftmargin=0.15in, label={}]")
            appendLine("    \\small{\\item{")

            val skillCategories = mutableListOf<String>()

            if (skills.languages.isNotEmpty()) {
                skillCategories.add("\\textbf{Languages}{: ${skills.languages.joinToString(", ")}}")
            }

            if (skills.frameworks.isNotEmpty()) {
                skillCategories.add("\\textbf{Frameworks}{: ${skills.frameworks.joinToString(", ")}}")
            }

            if (skills.technologies.isNotEmpty()) {
                skillCategories.add("\\textbf{Technologies}{: ${skills.technologies.joinToString(", ")}}")
            }

            if (skills.libraries.isNotEmpty()) {
                skillCategories.add("\\textbf{Libraries}{: ${skills.libraries.joinToString(", ")}}")
            }

            appendLine("     ${skillCategories.joinToString(" \\\\\n     ")}")
            appendLine("    }}")
            appendLine(" \\end{itemize}")
        }
    }

    private fun generateFooter(): String {
        return """

%-------------------------------------------
\end{document}
        """.trimIndent()
    }

    private fun extractUsernameFromUrl(url: String): String {
        return if (url.contains("linkedin.com")) {
            "linkedin.com/in/${url.substringAfterLast("/")}"
        } else if (url.contains("github.com")) {
            "github.com/${url.substringAfterLast("/")}"
        } else {
            url
        }
    }


}