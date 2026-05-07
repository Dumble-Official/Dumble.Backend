using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;

public sealed class AccountId : ValueObject
{
    public Guid Value { get; private set; }

    private AccountId()
    {}
    private AccountId(Guid value)
    {
        Value = value;
    }

    public static AccountId Create(Guid id)
    {
        if (id == Guid.Empty)
            throw new ArgumentException("AccountId cannot be empty", nameof(id));
        return new AccountId(id);
    }

    public override IEnumerable<object> GetEqualityComponents()
    {
        yield return Value;
    }

    public override string ToString() => Value.ToString();
}