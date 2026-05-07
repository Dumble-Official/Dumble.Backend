using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using MediatR;
using Name = Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects.Name;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.CreateBundle;

public class CreateBundleCommandHandler(ILoggedInUserService loggedInUserService, 
    IFileService fileService,
    IGenericRepository<Bundle, BundleId> bundlesRepository) : IRequestHandler<CreateBundleCommand, BundleId>
{
    public async Task<BundleId> Handle(CreateBundleCommand request, CancellationToken cancellationToken)
    {
        // 1. Get current logged In user
        var currentUser = loggedInUserService.GetCurrentUser();

        if (!currentUser.IsInAnyRole(UserType.GymOwner, UserType.Gym, UserType.Trainer))
            throw new UnauthorizedAccessException("Only gym owners, gyms, and trainers can create bundles");

        var ownerType = AccountIdentity.ToOwnerType(currentUser.UserType, currentUser.Roles);
        var accountId = AccountId.Create(AccountIdentity.ToAccountGuid(currentUser.Id));

        IEnumerable<BundleImage>? imageUrls = null;
        if (request.Images is { Count: > 0 })
        {
            var uploadTasks = request.Images.Select(async img =>
                BundleImage.Create(await fileService.UploadAsync(img.Content, img.FileName, img.ContentType)));

            imageUrls = await Task.WhenAll(uploadTasks);
        }
        
        // 4. Check for how Many Active Bundles the user and see if it is valid to his subscription or not
        var permission = true;
        
        // 5. Create the bundle
        var bundle = Bundle.Create(
            AccountId.Create(currentUser.Id),
            currentUser.AccountType,
            Name.Create(request.Name),
            Description.Create(request.Description),
            Price.Create(request.Price),
            Enum.Parse<Status>(request.Status, ignoreCase: true),
            CategoryId.Create(request.CategoryId),
            AccountId.Create(currentUser.Id),
            request.ExpiresOn,
            imageUrls
        );
        
        // 6. Persist the Changes to the database
        await bundlesRepository.Create(bundle);
        
       var rowsAffected = await bundlesRepository.CompleteAsync();

       return bundle.Id;
    }
}