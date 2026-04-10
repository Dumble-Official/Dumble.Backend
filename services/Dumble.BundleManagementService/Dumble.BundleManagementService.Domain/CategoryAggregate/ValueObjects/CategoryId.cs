using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;

public sealed class CategoryId : ValueObject
{
    public Guid Value { get; }

    private CategoryId(Guid value)
    {
        Value = value;
    }

    public static CategoryId CreateUnique()
    {
        return new CategoryId(Guid.NewGuid());
    }

    public static CategoryId Create(Guid id)
    {
        return new CategoryId(id);
    }

    public override IEnumerable<object> GetEqualityComponents()
    {
        yield return Value;
    }

    public override string ToString() => Value.ToString();
}
