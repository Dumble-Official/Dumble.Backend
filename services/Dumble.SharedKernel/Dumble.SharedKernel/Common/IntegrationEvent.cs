namespace Dumble.SharedKernel.Common;

public abstract record IntegrationEvent
{
    public Guid EventId { get; init; } = Guid.NewGuid();
    public DateTimeOffset OccurredOn { get; init; } = DateTimeOffset.UtcNow;
    public string? CorrelationId { get; init; }
    public int Version { get; init; } = 1;
}
