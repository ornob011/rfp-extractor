import { useState } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table';
import { ConfidenceBadge } from '@/components/ConfidenceBadge';
import {
    ENTITY_FIELDS,
    ENTITY_CATEGORY_LABELS,
    type EntityCategory,
    type BadgeThresholds,
} from '@/types/rfp';

interface EntityTableProps {
    entities: Record<string, unknown>;
    confidenceMap: Record<string, number>;
    badgeThresholds?: BadgeThresholds;
}

const CATEGORIES: EntityCategory[] = [
    'general',
    'submission',
    'financial',
    'ict',
    'staffing',
    'support',
    'evaluation',
];

function formatValue(value: unknown): string {
    if (value === null || value === undefined) {
        return '—';
    }

    if (typeof value === 'boolean') {
        return formatBoolean(value);
    }

    if (typeof value === 'object') {
        return JSON.stringify(value, null, 2);
    }

    return String(value);
}

export function EntityTable({ entities, confidenceMap, badgeThresholds }: EntityTableProps) {
    const [activeTab, setActiveTab] = useState<EntityCategory>('general');

    return (
        <Tabs
            value={activeTab}
            onValueChange={(value) => setActiveTab(value as EntityCategory)}
        >
            <TabsList className="flex flex-wrap gap-1">
                {CATEGORIES.map((category) => (
                    <TabsTrigger key={category} value={category}>
                        {ENTITY_CATEGORY_LABELS[category]}
                    </TabsTrigger>
                ))}
            </TabsList>

            {CATEGORIES.map((category) => (
                <TabsContent key={category} value={category}>
                    <CategoryTable
                        category={category}
                        entities={entities}
                        confidenceMap={confidenceMap}
                        badgeThresholds={badgeThresholds}
                    />
                </TabsContent>
            ))}
        </Tabs>
    );
}

function CategoryTable(
    {
        category,
        entities,
        confidenceMap,
        badgeThresholds,
    }: {
        category: EntityCategory;
        entities: Record<string, unknown>;
        confidenceMap: Record<string, number>;
        badgeThresholds?: BadgeThresholds;
    },
) {
    const fieldsForCategory = ENTITY_FIELDS.filter(
        (field) => field.category === category,
    );

    return (
        <Table>
            <TableHeader>
                <TableRow>
                    <TableHead className="w-[200px]">Field</TableHead>
                    <TableHead>Value</TableHead>
                    <TableHead className="w-[120px] text-right">Confidence</TableHead>
                </TableRow>
            </TableHeader>

            <TableBody>
                {fieldsForCategory.map((field) => {
                    const value = entities[field.key];
                    const score = confidenceMap[field.key] ?? 0;

                    return (
                        <TableRow key={field.key}>
                            <TableCell className="font-medium">
                                {field.label}
                            </TableCell>
                            <TableCell className="max-w-md whitespace-pre-wrap break-words">
                                {formatValue(value)}
                            </TableCell>
                            <TableCell className="text-right">
                                <ConfidenceBadge
                                    score={score}
                                    highThreshold={badgeThresholds?.high}
                                    mediumThreshold={badgeThresholds?.medium}
                                />
                            </TableCell>
                        </TableRow>
                    );
                })}
            </TableBody>
        </Table>
    );
}

function formatBoolean(value: boolean): string {
    switch (value) {
        case true:
            return 'Yes';
        case false:
            return 'No';
    }
}
