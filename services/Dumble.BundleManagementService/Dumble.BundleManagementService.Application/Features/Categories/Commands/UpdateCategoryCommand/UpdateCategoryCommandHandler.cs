using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.CategoryAggregate;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Categories.Commands.UpdateCategoryCommand;

public sealed class UpdateCategoryCommandHandler(
    IGenericRepository<Category, CategoryId> categoryRepository
    )
    : IRequestHandler<UpdateCategoryCommand, UpdateCategoryCommandResponse>
{
    public async Task<UpdateCategoryCommandResponse> Handle(UpdateCategoryCommand request, CancellationToken cancellationToken)
    {
        // 1. Get Category from the Database
        var categoryId = CategoryId.Create(request.Id);
        
        var category = await categoryRepository.Get(categoryId);

        // 2. Check if it exists or not
        if (category is null)
            throw new Exception("Not Found");

        // 3. Update the Domain Model
        category.Update(Name.Create(request.Name));
        
        // 4. Persist Changes to the database
        categoryRepository.Update(category);

        await categoryRepository.CompleteAsync();
        
        return new UpdateCategoryCommandResponse(category.Id.Value.ToString(), category.Name.Value);
    }
}