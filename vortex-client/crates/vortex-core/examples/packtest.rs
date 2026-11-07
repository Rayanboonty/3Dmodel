//! Time a parallel pack install: packtest <instance> <id|alt,id...>...
use std::time::Instant;
use vortex_core::{modrinth, paths::Paths, Launcher};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let mut args = std::env::args().skip(1);
    let name = args.next().expect("instance");
    let entries: Vec<Vec<String>> = args.map(|a| a.split('|').map(String::from).collect()).collect();
    let launcher = Launcher::new(Paths::new(std::env::var("VORTEX_HOME").map(Into::into).unwrap_or_else(|_| Paths::default_root())))?;
    let inst = launcher.instances().find(&name)?.expect("no such instance");
    let t = Instant::now();
    let added = std::sync::atomic::AtomicUsize::new(0);
    modrinth::install_pack(&launcher, &inst, &entries, |_| {}, |_, s, _| {
        if s == modrinth::PackState::Added { added.fetch_add(1, std::sync::atomic::Ordering::Relaxed); }
    }).await?;
    println!("{} of {} entries added in {:.1}s", added.into_inner(), entries.len(), t.elapsed().as_secs_f64());
    Ok(())
}
