

namespace Dumble.BundleManagementService.Domain.Common;

public abstract class AggregateRoot<TKey> : Entity<TKey> 
    where TKey : ValueObject
{
    protected AggregateRoot(TKey id) : base(id)
    {
    }

    protected AggregateRoot() { }

    protected readonly List<IDomainEvent> _domainEvents = new();

    private void RaiseDomainEvent(IDomainEvent @event)
    {
        _domainEvents.Add(@event);
    }

    public List<IDomainEvent> PopDomainEvents()
    {
        var copy = _domainEvents.ToList();
        _domainEvents.Clear();

        return copy;
    }
}