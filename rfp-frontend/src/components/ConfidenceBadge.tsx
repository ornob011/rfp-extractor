import { Badge } from '@/components/ui/badge';

const DEFAULT_HIGH = 0.8;
const DEFAULT_MEDIUM = 0.5;

interface ConfidenceBadgeProps {
    score: number;
    highThreshold?: number;
    mediumThreshold?: number;
}

export function ConfidenceBadge({
    score,
    highThreshold = DEFAULT_HIGH,
    mediumThreshold = DEFAULT_MEDIUM,
}: ConfidenceBadgeProps) {
    if (score >= highThreshold) {
        return (
            <Badge variant="outline" className="text-green-700 border-green-300">
                HIGH {Math.round(score * 100)}%
            </Badge>
        );
    }

    if (score >= mediumThreshold) {
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
