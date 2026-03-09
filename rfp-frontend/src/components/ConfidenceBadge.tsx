import { Badge } from '@/components/ui/badge';

interface ConfidenceBadgeProps {
    score: number;
}

export function ConfidenceBadge({ score }: ConfidenceBadgeProps) {
    if (score >= 0.8) {
        return (
            <Badge variant="outline" className="text-green-700 border-green-300">
                HIGH {Math.round(score * 100)}%
            </Badge>
        );
    }

    if (score >= 0.5) {
        return (
            <Badge variant="outline" className="text-yellow-700 border-yellow-300">
                MED {Math.round(score * 100)}%
            </Badge>
        );
    }

    return (
        <Badge variant="destructive">
            LOW {Math.round(score * 100)}%
        </Badge>
    );
}
