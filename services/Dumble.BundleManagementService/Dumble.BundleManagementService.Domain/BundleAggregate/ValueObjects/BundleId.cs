using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;

public sealed class BundleId : ValueObject
{
    public Guid Value { get; private set; }

    private BundleId(Guid value)
    {
        Value = value;
    }

    public static BundleId CreateUnique()
    {
        return new BundleId(Guid.NewGuid());
    }

    public static BundleId Create(Guid id)
    {
        return new BundleId(id);
    }

    public override IEnumerable<object> GetEqualityComponents()
    {
        yield return Value;
    }

    public override string ToString() => Value.ToString();
}