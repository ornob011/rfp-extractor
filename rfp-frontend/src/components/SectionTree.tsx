import { useState, type KeyboardEvent } from 'react';
import { ChevronDown, ChevronRight, Minus } from 'lucide-react';
import { ConfidenceBadge } from '@/components/ConfidenceBadge';
import type { Section } from '@/types/rfp';

interface SectionNodeProps {
    section: Section;
    depth: number;
}

function SectionNode({ section, depth }: SectionNodeProps) {
    const [expanded, setExpanded] = useState(depth === 0);
    const hasChildren = section.children.length > 0;

    const toggle = () => setExpanded((prev) => !prev);

    const handleKeyDown = (e: KeyboardEvent) => {
        if (e.key !== 'Enter' && e.key !== ' ') return;
        e.preventDefault();
        toggle();
    };

    return (
        <li>
            <div
                className="flex items-center gap-2 py-1.5 px-2 rounded hover:bg-muted cursor-pointer"
                role="treeitem"
                tabIndex={0}
                aria-expanded={hasChildren ? expanded : undefined}
                onClick={toggle}
                onKeyDown={handleKeyDown}
            >
                <span className="w-4 shrink-0 flex items-center justify-center text-muted-foreground">
                    {hasChildren && expanded && <ChevronDown className="h-4 w-4" />}
                    {hasChildren && !expanded && <ChevronRight className="h-4 w-4" />}
                    {!hasChildren && <Minus className="h-3 w-3" />}
                </span>

                <span className="text-xs font-mono text-muted-foreground shrink-0">
                    L{section.level}
                </span>

                <span className="font-medium text-foreground text-sm truncate">
                    {section.title}
                </span>

                <span className="text-xs text-muted-foreground ml-auto shrink-0">
                    pp. {section.pageStart}-{section.pageEnd}
                </span>

                {section.confidence && (
                    <ConfidenceBadge score={section.confidence.score} />
                )}
            </div>

            {expanded && hasChildren && (
                <ul className="border-l border-border ml-4" role="group">
                    {section.children.map((child) => (
                        <SectionNode key={child.id} section={child} depth={depth + 1} />
                    ))}
                </ul>
            )}
        </li>
    );
}

interface SectionTreeProps {
    sections: Section[];
}

export function SectionTree({ sections }: SectionTreeProps) {
    if (sections.length === 0) {
        return (
            <p className="text-muted-foreground text-sm italic py-4">
                No sections detected.
            </p>
        );
    }

    return (
        <ul className="space-y-0.5" role="tree">
            {sections.map((section) => (
                <SectionNode key={section.id} section={section} depth={0} />
            ))}
        </ul>
    );
}
