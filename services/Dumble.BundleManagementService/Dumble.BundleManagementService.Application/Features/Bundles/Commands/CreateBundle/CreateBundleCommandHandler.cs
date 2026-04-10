using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using MediatR;
using Microsoft.Extensions.Logging;
using Name = Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects.Name;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.CreateBundle;

public class CreateBundleCommandHandler(ILoggedInUserService loggedInUserService, 
    IFileService fileService,
    IGenericRepository<Bundle, BundleId> bundlesRepository, 
    ILogger<CreateBundleCommandHandler> logger) : IRequestHandler<CreateBundleCommand, BundleId>
{
    public async Task<BundleId> Handle(CreateBundleCommand request, CancellationToken cancellationToken)
    {
        // 1. Get current logged In user
        var currentUser = loggedInUserService.GetCurrentUser();
        
        // 2. Check his Permissions.
        if (currentUser.AccountType is not OwnerType.Gym or OwnerType.Trainer)
            throw new Exception("UnAuthorized");

        IEnumerable<BundleImage>? imageUrls = null;
        
        //  3. Upload Bundle Images.
        if (request.Images is not null)
        {
            var uploadTasks = request.Images.Select(async img =>
            {
                await using var stream = img.OpenReadStream();
                return BundleImage.Create(await fileService.UploadAsync(stream, img.Name, img.ContentType));
            });
            
            imageUrls = (await Task.WhenAll(uploadTasks));
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
            Enum.Parse<Status>(request.Status),
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