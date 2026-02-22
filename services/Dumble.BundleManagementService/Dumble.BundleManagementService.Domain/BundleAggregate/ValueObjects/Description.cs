using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;

public sealed class Description : ValueObject
{
    public string Value { get; private set; }

    private Description(){}
    private Description(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
            throw new ArgumentException("Description cannot be empty.", nameof(value));

        Value = value;
    }

    public static Description Create(string value)
    {
        return new Description(value);
    }

    public override IEnumerable<object> GetEqualityComponents()
    {
        yield return Value;
    }
}