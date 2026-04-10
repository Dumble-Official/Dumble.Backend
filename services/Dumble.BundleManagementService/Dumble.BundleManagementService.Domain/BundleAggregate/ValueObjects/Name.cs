using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;

public sealed class Name : ValueObject
{
    public string Value { get; private set; }

    private Name()
    {
        
    }
    private Name(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
            throw new ArgumentException("Name cannot be empty.", nameof(value));

        Value = value;
    }

    public static Name Create(string value)
    {
        return new Name(value);
    }

    public override IEnumerable<object> GetEqualityComponents()
    {
        yield return Value;
    }

}