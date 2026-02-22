using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.Common;
using Name = Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects.Name;

namespace Dumble.BundleManagementService.Domain.BundleAggregate;

public sealed class Bundle : AggregateRoot<BundleId>
{
    public AccountId OwnerId { get; private set; }
    public OwnerType OwnerType { get; private set; }
    public Name Name { get; private set; }
    public Description Description { get; private set; }
    public Price Price { get; private set; }
    public Status Status { get; private set; } = default!;
    public DateTime ExpiresOn { get; private set; }
    public DateTime CreatedOn { get; private set; }
    public DateTime LastModifiedOn { get; private set; }
    public AccountId CreatedBy { get; private set; }
    public AccountId LastModifiedBy { get; private set; }
    public CategoryId CategoryId { get; private set; }

    private readonly List<BundleImage> _images = new();
    private readonly List<AccountId> _viewers = new();

    public IReadOnlyCollection<BundleImage> Images => _images.AsReadOnly();
    public IReadOnlyCollection<AccountId> Viewers => _viewers.AsReadOnly();

    private Bundle(){}
    private Bundle(BundleId id,
                   AccountId ownerId,
                   OwnerType ownerType,
                   Name name,
                   Description description,
                   Price price,
                   Status status,
                   DateTime expiresOn,
                   DateTime createdOn,
                   DateTime lastModifiedOn,
                   AccountId createdBy,
                   AccountId lastModifiedBy,
                   CategoryId categoryId,
                   IEnumerable<BundleImage>? images = null,
                   IEnumerable<AccountId>? viewers = null) : base(id)
    {
        OwnerId = ownerId ?? throw new ArgumentNullException(nameof(ownerId));
        OwnerType = ownerType;
        Name = name ?? throw new ArgumentNullException(nameof(name));
        Description = description ?? throw new ArgumentNullException(nameof(description));
        Price = price ?? throw new ArgumentNullException(nameof(price));
        Status = status;
        ExpiresOn = expiresOn;
        CreatedOn = createdOn;
        LastModifiedOn = lastModifiedOn;
        CreatedBy = createdBy ?? throw new ArgumentNullException(nameof(createdBy));
        LastModifiedBy = lastModifiedBy ?? throw new ArgumentNullException(nameof(lastModifiedBy));
        CategoryId = categoryId ?? throw new ArgumentNullException(nameof(categoryId));

        if (images != null)
            foreach (var img in images) _images.Add(img);

        if (viewers != null)
            foreach (var v in viewers) _viewers.Add(v);
    }

    public static Bundle Create(
        AccountId ownerId,
        OwnerType ownerType,
        Name name,
        Description description,
        Price price,
        Status status,
        CategoryId categoryId,
        AccountId createdBy,
        DateTime? expiresOn = null,
        IEnumerable<BundleImage>? images = null,
        BundleId? bundleId = null)
    {
        var now = DateTime.UtcNow;
        var id = bundleId ?? BundleId.CreateUnique();
        var expiry = expiresOn ?? now.AddMonths(1);

        return new Bundle(
            id: id,
            ownerId: ownerId,
            ownerType: ownerType,
            name: name,
            description: description,
            price: price,
            status: status,
            expiresOn: expiry,
            createdOn: now,
            lastModifiedOn: now,
            createdBy: createdBy,
            lastModifiedBy: createdBy,
            categoryId: categoryId,
            images: images,
            viewers: null
        );
        
    }



    public bool View(AccountId viewerId)
    {
        if (_viewers.Contains(viewerId)) return false;
        _viewers.Add(viewerId);
        return true;
    }

}