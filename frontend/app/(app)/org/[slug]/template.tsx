/**
 * Per-navigation entrance for org pages.
 *
 * Next.js renders a template between the org layout and its pages, keyed by the segment
 * below org/[slug], so it is remounted — together with the new page, in the same commit —
 * when the user moves to another section (e.g. /projects -> /customers); navigation within
 * a section (e.g. /projects -> /projects/[id]) does not re-run the entrance. The page is
 * mounted exactly once per navigation and never rendered into an outgoing wrapper (as the
 * old pathname-keyed AnimatePresence did, which remounted the new page ~100ms after it
 * appeared and discarded anything the user had started). The fade is a plain CSS animation: it needs no JavaScript, so server-rendered
 * pages are not held invisible until hydration, and it is skipped for reduced motion.
 */
export default function OrgTemplate({ children }: { children: React.ReactNode }) {
  return (
    <div className="animate-in fade-in slide-in-from-bottom-[2px] duration-100 ease-[cubic-bezier(0.32,0.72,0,1)] motion-reduce:animate-none">
      {children}
    </div>
  );
}
