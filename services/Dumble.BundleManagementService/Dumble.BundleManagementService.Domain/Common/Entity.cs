namespace Dumble.BundleManagementService.Domain.Common;

public abstract class Entity<TKey>
where TKey : ValueObject
{
    public TKey Id { get; init; }

    public override bool Equals(object? other)
    {
        if (other is null || other.GetType() != GetType())
        {
            return false;
        }

        return ((Entity<TKey>)other).Id.Equals(Id);
    }

    public override int GetHashCode()
    {
        return Id.GetHashCode();
    }

    protected Entity(TKey id) => Id = id;

    protected Entity() { }
}