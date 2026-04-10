using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;

public sealed class BundleImage : ValueObject
{
    // EF requires a parameterless ctor
    private BundleImage() { }

    private BundleImage(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
            throw new ArgumentException("Image URL must not be empty.", nameof(value));

        Value = value;
    }

    public static BundleImage Create(string url)
    {
        return new BundleImage(url);
    }

    public string Value { get; private set; } = null!;

    public override IEnumerable<object> GetEqualityComponents()
    {
        yield return Value;
    }

}
