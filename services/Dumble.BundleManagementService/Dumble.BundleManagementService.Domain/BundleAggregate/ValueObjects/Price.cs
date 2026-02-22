using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;

public sealed class Price : ValueObject
{
    public decimal Value { get; private set; }

    private Price()
    {
        
    }
    private Price(decimal value)
    {
        if (value < 0)
            throw new ArgumentException("Price cannot be negative.", nameof(value));

        Value = value;
    }

    public static Price Create(decimal value)
    {
        return new Price(value);
    }

    public override IEnumerable<object> GetEqualityComponents()
    {
        yield return Value;
    }
}
