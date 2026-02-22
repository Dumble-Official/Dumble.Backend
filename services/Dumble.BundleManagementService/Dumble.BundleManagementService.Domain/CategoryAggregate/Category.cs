using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Domain.CategoryAggregate;

public sealed class Category : AggregateRoot<CategoryId>
{
    public Name Name { get; private set; }

    private Category(){}
    private Category(CategoryId id, Name name) : base(id)
    {
        Name = name;
    }

    public static Category Create(Name name)
    {
        return new Category(CategoryId.CreateUnique(), name);
    }
    
    public void Update(Name name)
    {
        this.Name = name;
    }
}