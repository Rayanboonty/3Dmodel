//! Game output parsing.
//!
//! Mojang's log4j configs (needed for the Log4Shell mitigation on 1.7–1.18)
//! print events to stdout as XML (`<log4j:Event …>`). We turn those back into
//! readable lines; anything else passes through untouched.

use std::fmt;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum LogLevel {
    Trace,
    Debug,
    Info,
    Warn,
    Error,
    Fatal,
}

impl LogLevel {
    fn parse(s: &str) -> Self {
        match s.to_ascii_uppercase().as_str() {
            "TRACE" => Self::Trace,
            "DEBUG" => Self::Debug,
            "WARN" | "WARNING" => Self::Warn,
            "ERROR" => Self::Error,
            "FATAL" => Self::Fatal,
            _ => Self::Info,
        }
    }
}

impl fmt::Display for LogLevel {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(match self {
            Self::Trace => "TRACE",
            Self::Debug => "DEBUG",
            Self::Info => "INFO",
            Self::Warn => "WARN",
            Self::Error => "ERROR",
            Self::Fatal => "FATAL",
        })
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LogLine {
    pub level: LogLevel,
    pub text: String,
}

#[derive(Debug, Default)]
pub struct LogParser {
    event: Option<String>,
}

impl LogParser {
    /// Feed one raw output line; returns zero or more finished lines.
    pub fn feed(&mut self, line: &str, stderr: bool) -> Vec<LogLine> {
        if let Some(buf) = &mut self.event {
            buf.push_str(line);
            buf.push('\n');
            if line.contains("</log4j:Event>") {
                let ev = self.event.take().unwrap_or_default();
                return parse_event(&ev);
            }
            return Vec::new();
        }
        let trimmed = line.trim_start();
        if trimmed.starts_with("<log4j:Event") {
            if trimmed.contains("</log4j:Event>") {
                return parse_event(trimmed);
            }
            self.event = Some(format!("{line}\n"));
            return Vec::new();
        }
        vec![LogLine {
            level: guess_level(line, stderr),
            text: line.to_string(),
        }]
    }
}

fn guess_level(line: &str, stderr: bool) -> LogLevel {
    if line.contains("/FATAL]") || line.contains("[FATAL]") {
        LogLevel::Fatal
    } else if line.contains("/ERROR]") || line.contains("[ERROR]") || line.contains("Exception") {
        LogLevel::Error
    } else if line.contains("/WARN]") || line.contains("[WARN]") || stderr {
        LogLevel::Warn
    } else {
        LogLevel::Info
    }
}

fn attr<'a>(s: &'a str, name: &str) -> Option<&'a str> {
    let pat = format!(" {name}=\"");
    let start = s.find(&pat)? + pat.len();
    let end = s[start..].find('"')? + start;
    Some(&s[start..end])
}

fn section(s: &str, tag: &str) -> Option<String> {
    let open = format!("<{tag}>");
    let close = format!("</{tag}>");
    let start = s.find(&open)? + open.len();
    let end = s[start..].find(&close)? + start;
    let inner = s[start..end].trim();
    Some(match inner.strip_prefix("<![CDATA[") {
        Some(rest) => rest.strip_suffix("]]>").unwrap_or(rest).to_string(),
        None => unescape(inner),
    })
}

fn unescape(s: &str) -> String {
    s.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

fn parse_event(ev: &str) -> Vec<LogLine> {
    let level = attr(ev, "level").map(LogLevel::parse).unwrap_or(LogLevel::Info);
    let thread = attr(ev, "thread").map(unescape).unwrap_or_else(|| "main".into());
    let message = section(ev, "log4j:Message").unwrap_or_default();
    let mut out = Vec::new();
    let mut lines = message.lines();
    out.push(LogLine {
        level,
        text: format!("[{thread}/{level}] {}", lines.next().unwrap_or("")),
    });
    out.extend(lines.map(|l| LogLine {
        level,
        text: l.to_string(),
    }));
    if let Some(t) = section(ev, "log4j:Throwable") {
        out.extend(t.lines().map(|l| LogLine {
            level,
            text: l.to_string(),
        }));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_multiline_xml_event() {
        let mut p = LogParser::default();
        assert!(p
            .feed(r#"<log4j:Event logger="net.minecraft.client.Minecraft" timestamp="1" level="INFO" thread="Render thread">"#, false)
            .is_empty());
        assert!(p.feed("  <log4j:Message><![CDATA[Setting user: Steve]]></log4j:Message>", false).is_empty());
        let out = p.feed("</log4j:Event>", false);
        assert_eq!(
            out,
            vec![LogLine { level: LogLevel::Info, text: "[Render thread/INFO] Setting user: Steve".into() }]
        );
    }

    #[test]
    fn parses_throwable_and_levels() {
        let mut p = LogParser::default();
        let ev = "<log4j:Event logger=\"x\" timestamp=\"1\" level=\"ERROR\" thread=\"main\">\n\
                  <log4j:Message><![CDATA[Boom\nsecond line]]></log4j:Message>\n\
                  <log4j:Throwable><![CDATA[java.lang.RuntimeException: x\n\tat Foo.bar]]></log4j:Throwable>\n\
                  </log4j:Event>";
        let mut out = Vec::new();
        for l in ev.lines() {
            out.extend(p.feed(l, false));
        }
        assert_eq!(out.len(), 4);
        assert_eq!(out[0].text, "[main/ERROR] Boom");
        assert_eq!(out[1].text, "second line");
        assert!(out.iter().all(|l| l.level == LogLevel::Error));
    }

    #[test]
    fn plain_lines_pass_through() {
        let mut p = LogParser::default();
        let out = p.feed("[12:00:00] [main/WARN]: hello", false);
        assert_eq!(out[0].level, LogLevel::Warn);
        assert_eq!(out[0].text, "[12:00:00] [main/WARN]: hello");
        assert_eq!(p.feed("LWJGL Version: 2.9.4", false)[0].level, LogLevel::Info);
    }
}
